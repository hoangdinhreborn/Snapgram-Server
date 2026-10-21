package com.example.payment.adapter.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class VnpaySigner {

    private VnpaySigner() {
    }

    /** Build hashData: sort key alphabet, URLEncode key=value, join '&'.
     *  Theo code mau chinh thuc cua VNPay: dung URLEncoder (space -> '+'),
     *  KHONG doi '+' thanh '%20' — doi se lech chu ky voi VNPay. */
    public static String buildHashData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign VNPay data", e);
        }
    }

    public static String sign(Map<String, String> params, String secret) {
        return hmacSha512(secret, buildHashData(params));
    }

    public static boolean verify(Map<String, String> params, String actualHash, String secret) {
        if (actualHash == null || actualHash.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        String expected = sign(params, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8));
    }

    /** Chi cac tham so vnp_* duoc ky — loai SecureHash/SecureHashType (callback + QueryDR). */
    public static Map<String, String> signable(Map<String, String> params) {
        Map<String, String> toSign = new HashMap<>();
        if (params == null) {
            return toSign;
        }
        params.forEach((k, v) -> {
            if (k != null && k.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(k) && !"vnp_SecureHashType".equals(k)) {
                toSign.put(k, v);
            }
        });
        return toSign;
    }

    /**
     * Raw string checksum cua response QueryDR — thu tu field CO DINH, noi bang '|',
     * KHONG URL-encode (khac callback): ResponseId|Command|ResponseCode|Message|TmnCode|
     * TxnRef|Amount|BankCode|PayDate|TransactionNo|TransactionType|TransactionStatus|
     * OrderInfo|PromotionCode|PromotionAmount.
     */
    public static String queryDrResponseRaw(Map<String, String> response, String tmnCode) {
        return String.join("|",
                value(response, "vnp_ResponseId"),
                value(response, "vnp_Command"),
                value(response, "vnp_ResponseCode"),
                value(response, "vnp_Message"),
                tmnCode == null ? "" : tmnCode,
                value(response, "vnp_TxnRef"),
                value(response, "vnp_Amount"),
                value(response, "vnp_BankCode"),
                value(response, "vnp_PayDate"),
                value(response, "vnp_TransactionNo"),
                value(response, "vnp_TransactionType"),
                value(response, "vnp_TransactionStatus"),
                value(response, "vnp_OrderInfo"),
                value(response, "vnp_PromotionCode"),
                value(response, "vnp_PromotionAmount"));
    }

    /** Verify vnp_SecureHash cua response QueryDR (neu VNPay co tra ve). */
    public static boolean verifyQueryDrResponse(Map<String, String> response, String tmnCode, String secret) {
        if (response == null || response.isEmpty() || secret == null || secret.isBlank()) {
            return false;
        }
        String receivedHash = response.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String expected = hmacSha512(secret, queryDrResponseRaw(response, tmnCode));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                receivedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(Map<String, String> response, String key) {
        String value = response.get(key);
        return value == null ? "" : value;
    }

    private static String encode(String value) {
        // Giunguyen '+' cho space theo sample VNPay (sortObject: encodeURIComponent + replace %20 -> '+')
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
