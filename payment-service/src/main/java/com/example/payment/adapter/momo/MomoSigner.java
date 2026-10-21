package com.example.payment.adapter.momo;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * HMAC_SHA256 chu ky MoMo.
 * Raw signature dung thu tu CO DINH theo docs MoMo (khong sort alphabet nhu VNPay),
 * moi field gan bang "&" va khong URL-encode.
 */
public final class MomoSigner {

    private MomoSigner() {
    }

    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign MoMo data", e);
        }
    }

    /** Raw cho POST /v2/gateway/api/create. */
    public static String createRaw(String accessKey, String amount, String extraData, String ipnUrl,
                                   String orderId, String orderInfo, String partnerCode, String redirectUrl,
                                   String requestId, String requestType) {
        return "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + nullToEmpty(extraData)
                + "&ipnUrl=" + nullToEmpty(ipnUrl)
                + "&orderId=" + nullToEmpty(orderId)
                + "&orderInfo=" + nullToEmpty(orderInfo)
                + "&partnerCode=" + nullToEmpty(partnerCode)
                + "&redirectUrl=" + nullToEmpty(redirectUrl)
                + "&requestId=" + nullToEmpty(requestId)
                + "&requestType=" + nullToEmpty(requestType);
    }

    /** Raw cho POST /v2/gateway/api/query. */
    public static String queryRaw(String accessKey, String orderId, String partnerCode, String requestId) {
        return "accessKey=" + accessKey
                + "&orderId=" + nullToEmpty(orderId)
                + "&partnerCode=" + nullToEmpty(partnerCode)
                + "&requestId=" + nullToEmpty(requestId);
    }

    /**
     * Raw cho IPN va redirect (return URL) — hai kenh dung cung dinh dang.
     * Field thieu/nullable gan rong de chu ky on dinh.
     */
    public static String callbackRaw(Map<String, String> params, String accessKey) {
        return "accessKey=" + accessKey
                + "&amount=" + value(params, "amount")
                + "&extraData=" + value(params, "extraData")
                + "&message=" + value(params, "message")
                + "&orderId=" + value(params, "orderId")
                + "&orderInfo=" + value(params, "orderInfo")
                + "&orderType=" + value(params, "orderType")
                + "&partnerCode=" + value(params, "partnerCode")
                + "&payType=" + value(params, "payType")
                + "&requestId=" + value(params, "requestId")
                + "&responseTime=" + value(params, "responseTime")
                + "&resultCode=" + value(params, "resultCode")
                + "&transId=" + value(params, "transId");
    }

    public static String createSignature(String secretKey, String accessKey, String amount, String extraData,
                                         String ipnUrl, String orderId, String orderInfo, String partnerCode,
                                         String redirectUrl, String requestId, String requestType) {
        return hmacSha256(secretKey,
                createRaw(accessKey, amount, extraData, ipnUrl, orderId, orderInfo, partnerCode,
                        redirectUrl, requestId, requestType));
    }

    public static boolean verifyCallback(Map<String, String> params, String accessKey, String secretKey) {
        String received = params == null ? null : params.get("signature");
        if (received == null || received.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String expected = hmacSha256(secretKey, callbackRaw(params, accessKey));
        // Constant-time de khong mo timing oracle
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(Map<String, String> params, String key) {
        return nullToEmpty(params.get(key));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
