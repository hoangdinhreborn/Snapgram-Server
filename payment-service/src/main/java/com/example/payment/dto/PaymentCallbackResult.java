package com.example.payment.dto;

import java.time.Instant;

public record PaymentCallbackResult(
        boolean signatureValid,
        String error,
        String orderId,
        long amount,
        String responseCode,
        String transactionNo,
        String bankCode,
        String tmnCode,
        Instant payDate
) {
    public static PaymentCallbackResult invalid(String error) {
        return new PaymentCallbackResult(false, error, null, 0, null, null, null, null, null);
    }

    public static PaymentCallbackResult valid(String orderId, long amount, String responseCode,
                                              String transactionNo, String bankCode, String tmnCode,
                                              Instant payDate) {
        return new PaymentCallbackResult(true, null, orderId, amount, responseCode,
                transactionNo, bankCode, tmnCode, payDate);
    }

    public boolean isSuccessCode() {
        return "00".equals(responseCode);
    }
}
