package com.example.payment.adapter.vnpay;

import com.example.payment.dto.payment.PaymentCallbackResult;
import com.example.payment.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class VnpayPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "VNPAY";

    private static final DateTimeFormatter VNPAY_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnpayProperties properties;
    private final VnpayQueryClient queryClient;

    /** Format Instant -> yyyyMMddHHmmss (Asia/Ho_Chi_Minh) cho QueryDR. */
    public static String formatPayDate(Instant instant) {
        return VNPAY_FMT.format(instant);
    }

    /** Parse yyyyMMddHHmmss (Asia/Ho_Chi_Minh) -> Instant, sai dinh dang tra null. */
    public static Instant parsePayDate(String payDateStr) {
        if (payDateStr == null || payDateStr.isBlank()) {
            return null;
        }
        try {
            return VNPAY_FMT.parse(payDateStr, Instant::from);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String createPayment(CreatePaymentCommand command) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("VNPay is not configured (vnpay.tmn-code/hash-secret)");
        }
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(command.amount() * 100L));
        params.put("vnp_CurrCode", properties.getCurrency());
        params.put("vnp_TxnRef", command.orderId());
        params.put("vnp_OrderInfo", command.orderInfo());
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", command.clientIp());
        params.put("vnp_CreateDate", VNPAY_FMT.format(command.createDate()));
        params.put("vnp_ExpireDate", VNPAY_FMT.format(command.expireDate()));

        String hashData = VnpaySigner.buildHashData(new TreeMap<>(params));
        String secureHash = VnpaySigner.hmacSha512(properties.getHashSecret(), hashData);
        String paymentUrl = properties.getUrl() + "?" + hashData + "&vnp_SecureHash=" + secureHash;
        return paymentUrl;
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return PaymentCallbackResult.invalid("Missing callback params");
        }
        String receivedHash = params.get("vnp_SecureHash");

        // Chi ky cac param vnp_*, loai tru SecureHash/SecureHashType
        Map<String, String> toSign = VnpaySigner.signable(params);

        if (!VnpaySigner.verify(toSign, receivedHash, properties.getHashSecret())) {
            return PaymentCallbackResult.invalid("Invalid signature");
        }

        // Dung merchant code — vi tri nay tuong ung partnerCode cua MoMo
        if (properties.getTmnCode() != null && !properties.getTmnCode().isBlank()
                && !properties.getTmnCode().equals(params.get("vnp_TmnCode"))) {
            return PaymentCallbackResult.invalid("Merchant code mismatch");
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return PaymentCallbackResult.invalid("Missing vnp_TxnRef");
        }

        String responseCode = params.get("vnp_ResponseCode");
        if (responseCode == null || responseCode.isBlank()) {
            return PaymentCallbackResult.invalid("Missing vnp_ResponseCode");
        }

        // VNPay luon gui ca 2 ma — thieu TransactionStatus thi khong du can cu ket luan thanh cong
        String transactionStatus = params.get("vnp_TransactionStatus");
        if (transactionStatus == null || transactionStatus.isBlank()) {
            return PaymentCallbackResult.invalid("Missing vnp_TransactionStatus");
        }

        long amount;
        try {
            amount = Long.parseLong(params.getOrDefault("vnp_Amount", "0")) / 100L;
        } catch (NumberFormatException e) {
            return PaymentCallbackResult.invalid("Invalid vnp_Amount");
        }

        Instant payDate = parsePayDate(params.get("vnp_PayDate"));

        return PaymentCallbackResult.valid(
                txnRef,
                amount,
                responseCode,
                transactionStatus,
                params.get("vnp_TransactionNo"),
                params.get("vnp_BankCode"),
                payDate);
    }

    /** QueryDR -> ket qua chuan hoa cho reconcile. */
    @Override
    public QueryResult queryTransaction(QueryCommand command) {
        Map<String, String> resp = queryClient.queryTransaction(
                command.orderId(), command.orderInfo(), command.createDate());
        if (!command.orderId().equals(resp.get("vnp_TxnRef"))) {
            throw new IllegalStateException("TxnRef mismatch from VNPay");
        }
        if (!"00".equals(resp.get("vnp_ResponseCode"))) {
            return new QueryResult(false, false, resp.get("vnp_ResponseCode"), null, null, null,
                    0L, resp.get("vnp_Message"));
        }
        long amount;
        try {
            amount = Long.parseLong(resp.getOrDefault("vnp_Amount", "0")) / 100L;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid amount from VNPay");
        }
        boolean success = "00".equals(resp.get("vnp_TransactionStatus"));
        return new QueryResult(true, success, "00",
                resp.get("vnp_TransactionNo"), resp.get("vnp_BankCode"),
                parsePayDate(resp.get("vnp_PayDate")), amount,
                success ? resp.get("vnp_Message") : resp.get("vnp_TransactionStatus"));
    }

    /** RspCode la format IPN cua VNPay (00/01/02/04/97). */
    @Override
    public Map<String, String> ipnAck(IpnOutcome outcome) {
        return switch (outcome) {
            case HANDLED -> ack("00", "Confirm Success");
            case ORDER_NOT_FOUND -> ack("01", "Order not found");
            case ALREADY_CONFIRMED -> ack("02", "Order already confirmed");
            case INVALID_AMOUNT -> ack("04", "Invalid amount");
            case INVALID_SIGNATURE -> ack("97", "Invalid signature");
        };
    }

    private static Map<String, String> ack(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
}
