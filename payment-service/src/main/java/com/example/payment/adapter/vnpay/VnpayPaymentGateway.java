package com.example.payment.adapter.vnpay;

import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.dto.PaymentUrl;
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

    private static final DateTimeFormatter VNPAY_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final DateTimeFormatter VNPAY_PARSE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

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
            return VNPAY_PARSE_FMT.parse(payDateStr, Instant::from);
        } catch (Exception e) {
            return null;
        }
    }

    private final VnpayProperties properties;

    @Override
    public PaymentUrl createPayment(CreatePaymentCommand command) {
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
        return new PaymentUrl(paymentUrl, command.expireDate());
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return PaymentCallbackResult.invalid("Missing callback params");
        }
        String receivedHash = params.get("vnp_SecureHash");

        // Chi ky cac param vnp_*, loai tru SecureHash/SecureHashType
        Map<String, String> toSign = new HashMap<>();
        params.forEach((k, v) -> {
            if (k != null && k.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(k) && !"vnp_SecureHashType".equals(k)) {
                toSign.put(k, v);
            }
        });

        if (!VnpaySigner.verify(toSign, receivedHash, properties.getHashSecret())) {
            return PaymentCallbackResult.invalid("Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return PaymentCallbackResult.invalid("Missing vnp_TxnRef");
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
                params.get("vnp_ResponseCode"),
                params.get("vnp_TransactionNo"),
                params.get("vnp_BankCode"),
                params.get("vnp_TmnCode"),
                payDate);
    }
}
