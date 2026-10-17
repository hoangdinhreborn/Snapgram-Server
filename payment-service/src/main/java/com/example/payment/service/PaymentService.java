package com.example.payment.service;

import com.example.payment.adapter.vnpay.VnpayProperties;
import com.example.payment.adapter.vnpay.VnpayPaymentGateway;
import com.example.payment.adapter.vnpay.VnpayQueryClient;
import com.example.payment.dto.CreatePaymentResponse;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.dto.PaymentOrderResponse;
import com.example.payment.dto.PaymentUrl;
import com.example.payment.entity.PaymentEventLog;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.NotFoundException;
import com.example.payment.port.PaymentCatalog;
import com.example.payment.port.PaymentEventPublisher;
import com.example.payment.port.PaymentGateway;
import com.example.payment.port.ProductPrice;
import com.example.payment.repository.PaymentEventLogRepository;
import com.example.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentEventLogRepository eventLogRepository;
    private final PaymentCatalog catalog;
    private final PaymentGateway gateway;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentStateMachine stateMachine;
    private final VnpayProperties vnpayProperties;
    private final VnpayQueryClient queryClient;

    @Value("${payment.pending-timeout-minutes:15}")
    private long pendingTimeoutMinutes;

    @Transactional
    public CreatePaymentResponse createPayment(UUID userId, String productCode, String clientIp) {
        ProductPrice product = catalog.getProduct(productCode);

        PaymentOrder order = new PaymentOrder();
        order.setUserId(userId);
        // TxnRef: hex 32 ky tu, unique + kho doan
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setProvider("VNPAY");
        order.setProductCode(product.productCode());
        order.setAmount(product.amount());
        order.setCurrency(product.currency());
        order.setOrderInfo("Thanh toan " + product.productCode());
        order.setStatus(PaymentStatus.PENDING);
        Instant now = Instant.now();
        order.setExpiredAt(now.plusSeconds(pendingTimeoutMinutes * 60L));
        orderRepository.save(order);

        PaymentUrl paymentUrl = gateway.createPayment(new PaymentGateway.CreatePaymentCommand(
                order.getId(),
                order.getOrderId(),
                order.getAmount(),
                order.getOrderInfo(),
                clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp,
                now,
                order.getExpiredAt()));

        return new CreatePaymentResponse(
                order.getOrderId(), paymentUrl.url(), order.getStatus().name(), order.getExpiredAt());
    }

    /**
     * Xu ly IPN tu VNPay. Nguon duy nhat duoc phep chuyen PENDING -> SUCCESS.
     * Ma RspCode tra ve cho VNPay: 00, 01, 02, 04, 97.
     */
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        PaymentCallbackResult callback = gateway.verifyCallback(params);
        if (!callback.signatureValid()) {
            return rsp("97", "Invalid signature");
        }
        // Dung merchant code
        if (vnpayProperties.getTmnCode() != null && !vnpayProperties.getTmnCode().isBlank()
                && callback.tmnCode() != null && !callback.tmnCode().equals(vnpayProperties.getTmnCode())) {
            return rsp("01", "Order not found");
        }

        PaymentOrder order = orderRepository.findByOrderIdForUpdate(callback.orderId())
                .orElse(null);
        if (order == null) {
            return rsp("01", "Order not found");
        }
        // Dung amount server-side
        if (callback.amount() != order.getAmount()) {
            return rsp("04", "Invalid amount");
        }
        // Idempotent: order da xu ly thi bao VNPay dung retry
        if (order.getStatus() != PaymentStatus.PENDING) {
            return rsp("02", "Order already confirmed");
        }

        if (callback.isSuccessCode()) {
            stateMachine.checkTransition(order.getStatus(), PaymentStatus.SUCCESS);
            boolean completed = markSuccess(order, callback.responseCode(), callback.transactionNo(),
                    callback.bankCode(), callback.payDate(),
                    "vnpay:" + order.getOrderId() + ":" + callback.transactionNo());
            if (!completed) {
                // Race 2 IPN cung luc: unique event_id chan trung -> coi nhu da xu ly
                return rsp("02", "Order already confirmed");
            }
        } else {
            stateMachine.checkTransition(order.getStatus(), PaymentStatus.FAILED);
            order.setStatus(PaymentStatus.FAILED);
            order.setVnpayResponseCode(callback.responseCode());
            order.setVnpayTransactionNo(callback.transactionNo());
            order.setVnpayBankCode(callback.bankCode());
            orderRepository.save(order);
        }
        return rsp("00", "Confirm Success");
    }

    /** Return URL: chi verify chu ky de hien thi, KHONG doi trang thai DB. */
    @Transactional(readOnly = true)
    public PaymentCallbackResult previewReturn(Map<String, String> params) {
        return gateway.verifyCallback(params);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getOrder(UUID userId, boolean isAdmin, String orderId) {
        PaymentOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new NotFoundException("Order not found");
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<PaymentOrderResponse> listMine(UUID userId, Pageable pageable) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    /** Admin: loc don theo status/userId/productCode. */
    @Transactional(readOnly = true)
    public Page<PaymentOrderResponse> listAll(String status, UUID userId, String productCode, Pageable pageable) {
        return orderRepository.findAll((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), PaymentStatus.valueOf(status.trim().toUpperCase())));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(root.get("productCode"), productCode.trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable).map(this::toResponse);
    }

    /** Scheduler goi moi phut: PENDING qua han -> EXPIRED. Tra ve so don da chuyen. */
    @Transactional
    public int expireOverduePayments() {
        return orderRepository.expireOverdue(PaymentStatus.PENDING, PaymentStatus.EXPIRED, Instant.now());
    }

    /**
     * Admin doi chieu thu cong voi VNPay qua QueryDR (khi IPN that lac).
     * Chi cho phep tu PENDING/EXPIRED/FAILED. IPN binh thuong khong dung ham nay.
     */
    @Transactional
    public PaymentOrderResponse reconcile(String orderId) {
        PaymentOrder order = orderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == PaymentStatus.SUCCESS) {
            return toResponse(order);
        }
        if (order.getStatus() != PaymentStatus.PENDING
                && order.getStatus() != PaymentStatus.EXPIRED
                && order.getStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Order cannot be reconciled from status " + order.getStatus());
        }

        Map<String, String> resp = queryClient.queryTransaction(
                order.getOrderId(), order.getOrderInfo(), order.getCreatedAt());
        if (!"00".equals(resp.get("vnp_ResponseCode"))) {
            throw new NotFoundException("Transaction not found at VNPay: " + resp.get("vnp_Message"));
        }
        if (!"00".equals(resp.get("vnp_TransactionStatus"))) {
            throw new IllegalStateException(
                    "VNPay reports transaction not successful: " + resp.get("vnp_TransactionStatus"));
        }
        if (!order.getOrderId().equals(resp.get("vnp_TxnRef"))) {
            throw new IllegalStateException("TxnRef mismatch from VNPay");
        }
        long amount;
        try {
            amount = Long.parseLong(resp.getOrDefault("vnp_Amount", "0")) / 100L;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid amount from VNPay");
        }
        if (amount != order.getAmount()) {
            throw new IllegalStateException("Amount mismatch from VNPay");
        }

        boolean completed = markSuccess(order, "00", resp.get("vnp_TransactionNo"),
                resp.get("vnp_BankCode"), VnpayPaymentGateway.parsePayDate(resp.get("vnp_PayDate")),
                "querydr:" + order.getOrderId() + ":" + resp.get("vnp_TransactionNo"));
        if (!completed) {
            return toResponse(orderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new NotFoundException("Order not found")));
        }
        return toResponse(order);
    }

    /**
     * Chuyen order sang SUCCESS + ghi event log + publish event.
     * Tra ve false neu event log trung (race) — caller coi nhu da xu ly.
     */
    private boolean markSuccess(PaymentOrder order, String responseCode, String transactionNo,
                                String bankCode, Instant payDate, String logEventId) {
        order.setStatus(PaymentStatus.SUCCESS);
        order.setVnpayResponseCode(responseCode);
        order.setVnpayTransactionNo(transactionNo);
        order.setVnpayBankCode(bankCode);
        order.setVnpayPayDate(payDate);
        order.setProviderTransactionNo(transactionNo);
        // eventId stable, sinh 1 lan duy nhat
        if (order.getCompletedEventId() == null) {
            order.setCompletedEventId(UUID.randomUUID().toString());
        }
        orderRepository.save(order);

        PaymentEventLog log = new PaymentEventLog();
        log.setEventId(logEventId);
        log.setOrderId(order.getOrderId());
        log.setProvider("VNPAY");
        log.setResponseCode(responseCode);
        log.setTransactionNo(transactionNo);
        try {
            eventLogRepository.saveAndFlush(log);
        } catch (Exception e) {
            return false;
        }

        eventPublisher.publishPaymentCompleted(order);
        return true;
    }

    private PaymentOrderResponse toResponse(PaymentOrder order) {
        return new PaymentOrderResponse(
                order.getId(), order.getOrderId(), order.getProductCode(),
                order.getAmount(), order.getCurrency(),
                order.getStatus().name(), order.getCreatedAt(), order.getExpiredAt());
    }

    private Map<String, String> rsp(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
}
