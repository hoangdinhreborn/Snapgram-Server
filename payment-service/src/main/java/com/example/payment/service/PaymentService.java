package com.example.payment.service;

import com.example.payment.dto.payment.CreatePaymentResponse;
import com.example.payment.dto.payment.PaymentCallbackResult;
import com.example.payment.dto.payment.PaymentOrderResponse;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.NotFoundException;
import com.example.payment.port.PaymentCatalog;
import com.example.payment.port.PaymentGateway;
import com.example.payment.port.PaymentGatewayRegistry;
import com.example.payment.port.PaymentGateway.IpnOutcome;
import com.example.payment.port.PaymentGateway.QueryCommand;
import com.example.payment.port.PaymentGateway.QueryResult;
import com.example.payment.port.ProductPrice;
import com.example.payment.repository.PaymentEventLogRepository;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.repository.PaymentOutboxRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentEventLogRepository eventLogRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentCatalog catalog;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentStateMachine stateMachine;

    @Value("${payment.pending-timeout-minutes:15}")
    private long pendingTimeoutMinutes;

    @Transactional
    public CreatePaymentResponse createPayment(UUID userId, String productCode, String clientIp, String provider) {
        PaymentGateway gateway = gatewayRegistry.get(provider);
        ProductPrice product = catalog.getProduct(productCode);

        PaymentOrder order = new PaymentOrder();
        order.setUserId(userId);
        // TxnRef: hex 32 ky tu, unique + kho doan
        order.setOrderId(UUID.randomUUID().toString().replace("-", ""));
        order.setProvider(gateway.provider());
        order.setProductCode(product.productCode());
        order.setAmount(product.amount());
        order.setCurrency(product.currency());
        order.setOrderInfo("Thanh toan " + product.productCode());
        order.setStatus(PaymentStatus.PENDING);
        Instant now = Instant.now();
        order.setExpiredAt(now.plusSeconds(pendingTimeoutMinutes * 60L));
        orderRepository.save(order);

        String paymentUrl = gateway.createPayment(new PaymentGateway.CreatePaymentCommand(
                order.getOrderId(),
                order.getAmount(),
                order.getOrderInfo(),
                clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp,
                now,
                order.getExpiredAt()));

        return new CreatePaymentResponse(
                order.getOrderId(), paymentUrl, order.getStatus().name(), order.getExpiredAt());
    }

    /**
     * Xu ly IPN tu provider (VNPay GET, MoMo POST JSON). Nguon duy nhat duoc phep
     * chuyen PENDING -> SUCCESS. Tra ve ack theo format provider (RspCode / resultCode).
     */
    @Transactional
    public Map<String, String> handleIpn(String provider, Map<String, String> params) {
        PaymentGateway gateway = gatewayRegistry.get(provider);
        PaymentCallbackResult callback = gateway.verifyCallback(params);
        IpnOutcome outcome;

        if (!callback.signatureValid()) {
            log.warn("Rejected {} IPN: {}", provider, callback.error());
            outcome = IpnOutcome.INVALID_SIGNATURE;
        } else {
            PaymentOrder order = orderRepository.findByOrderIdForUpdate(callback.orderId()).orElse(null);
            if (order == null) {
                outcome = IpnOutcome.ORDER_NOT_FOUND;
            } else if (callback.amount() != order.getAmount()) {
                outcome = IpnOutcome.INVALID_AMOUNT;
            } else if (order.getStatus() == PaymentStatus.SUCCESS) {
                // Idempotent: don da thanh cong, bao provider dung retry
                outcome = IpnOutcome.ALREADY_CONFIRMED;
            } else if (callback.isSuccessCode()) {
                if (order.getStatus() != PaymentStatus.PENDING) {
                    // IPN den tre sau khi scheduler da EXPIRED, hoac lan tra truoc FAILED
                    // nhung khach da tra tien o lan sau. Provider xac nhan da thu tien
                    // (da verify chu ky + amount) -> phai cap quyen, khong duoc bo.
                    log.warn("{} IPN success for order {} in status {} — marking SUCCESS",
                            provider, order.getOrderId(), order.getStatus());
                }
                stateMachine.checkTransition(order.getStatus(), PaymentStatus.SUCCESS);
                boolean completed = markSuccess(order, callback.responseCode(), callback.transactionNo(),
                        callback.bankCode(), callback.payDate(),
                        logEventId(order.getProvider(), order.getOrderId(), callback.transactionNo()));
                // Race 2 IPN cung luc: unique event_id chan trung -> coi nhu da xu ly
                outcome = completed ? IpnOutcome.HANDLED : IpnOutcome.ALREADY_CONFIRMED;
            } else if (order.getStatus() == PaymentStatus.PENDING) {
                stateMachine.checkTransition(order.getStatus(), PaymentStatus.FAILED);
                order.setStatus(PaymentStatus.FAILED);
                order.setProviderResponseCode(callback.responseCode());
                order.setProviderChannel(callback.bankCode());
                orderRepository.save(order);
                outcome = IpnOutcome.HANDLED;
            } else {
                // IPN bao fail cho don da EXPIRED/FAILED/CANCELLED: giu nguyen trang thai
                outcome = IpnOutcome.ALREADY_CONFIRMED;
            }
        }
        return gateway.ipnAck(outcome);
    }

    /** Return URL: chi verify chu ky de hien thi, KHONG doi trang thai DB. */
    @Transactional(readOnly = true)
    public PaymentCallbackResult previewReturn(String provider, Map<String, String> params) {
        return gatewayRegistry.get(provider).verifyCallback(params);
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
            var predicates = new ArrayList<Predicate>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), PaymentStatus.valueOf(status.trim().toUpperCase())));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(root.get("productCode"), productCode.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(this::toResponse);
    }

    /** Scheduler goi moi phut: PENDING qua han -> EXPIRED. Tra ve so don da chuyen. */
    @Transactional
    public int expireOverduePayments() {
        return orderRepository.expireOverdue(PaymentStatus.PENDING, PaymentStatus.EXPIRED, Instant.now());
    }

    /**
     * Admin doi chieu thu cong voi provider (VNPay QueryDR / MoMo query) khi IPN that lac.
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

        PaymentGateway gateway = gatewayRegistry.get(order.getProvider());
        String provider = gateway.provider();
        QueryResult result = gateway.queryTransaction(
                new QueryCommand(order.getOrderId(), order.getOrderInfo(), order.getCreatedAt()));
        if (!result.found()) {
            throw new NotFoundException("Transaction not found at " + provider + ": " + result.message());
        }
        if (!result.success()) {
            throw new IllegalStateException(
                    provider + " reports transaction not successful: " + result.message());
        }
        if (result.amount() != order.getAmount()) {
            throw new IllegalStateException("Amount mismatch from " + provider);
        }

        markSuccess(order, result.responseCode(), result.transactionNo(),
                result.channel(), result.payDate(),
                logEventId(provider, order.getOrderId(), result.transactionNo()));
        return toResponse(order);
    }

    /**
     * Chuyen order sang SUCCESS + ghi event log (idempotency) + outbox event.
     * Tra ve false neu event log trung (race) — caller coi nhu da xu ly.
     */
    private boolean markSuccess(PaymentOrder order, String responseCode, String transactionNo,
                                String channel, Instant payDate, String logEventId) {
        order.setStatus(PaymentStatus.SUCCESS);
        order.setProviderResponseCode(responseCode);
        order.setProviderChannel(channel);
        order.setProviderPayDate(payDate);
        order.setProviderTransactionNo(transactionNo);
        // eventId stable, sinh 1 lan duy nhat
        if (order.getCompletedEventId() == null) {
            order.setCompletedEventId(UUID.randomUUID().toString());
        }
        orderRepository.save(order);

        int inserted = eventLogRepository.insertIfAbsent(
                UUID.randomUUID(), logEventId, order.getOrderId(), order.getProvider(),
                responseCode, transactionNo, Instant.now());
        if (inserted == 0) {
            return false;
        }

        // Cung transaction voi event log: Kafka loi thi dispatcher gui lai tu outbox,
        // khong con canh event log da ghi ma event bi mat.
        outboxRepository.insertIfAbsent(
                UUID.randomUUID(), order.getOrderId(), logEventId, Instant.now());
        return true;
    }

    /** Event id stable theo provider: vnpay:{orderId}:{txn} — dung cho ca IPN va reconcile. */
    private String logEventId(String provider, String orderId, String transactionNo) {
        return provider.toLowerCase(Locale.ROOT) + ":" + orderId + ":" + (transactionNo == null ? "" : transactionNo);
    }

    private PaymentOrderResponse toResponse(PaymentOrder order) {
        return new PaymentOrderResponse(
                order.getId(), order.getOrderId(), order.getProductCode(),
                order.getAmount(), order.getCurrency(),
                order.getStatus().name(), order.getCreatedAt(), order.getExpiredAt());
    }
}
