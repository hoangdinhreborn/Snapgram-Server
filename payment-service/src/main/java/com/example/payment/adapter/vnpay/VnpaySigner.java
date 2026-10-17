package com.example.payment.adapter.vnpay;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private static String encode(String value) {
        // Giunguyen '+' cho space theo sample VNPay (sortObject: encodeURIComponent + replace %20 -> '+')
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
