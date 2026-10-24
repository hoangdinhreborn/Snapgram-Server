package com.example.payment.dto;

import java.time.Instant;

public record PaymentCallbackResult(
        boolean signatureValid,
        String error,
        String orderId,
        long amount,
        String responseCode,
        String transactionStatus,
        String transactionNo,
        String bankCode,
        Instant payDate
) {
    public static PaymentCallbackResult invalid(String error) {
        return new PaymentCallbackResult(false, error, null, 0, null, null, null, null, null);
    }

    public static PaymentCallbackResult valid(String orderId, long amount, String responseCode,
                                              String transactionStatus, String transactionNo,
                                              String bankCode, Instant payDate) {
        return new PaymentCallbackResult(true, null, orderId, amount, responseCode,
                transactionStatus, transactionNo, bankCode, payDate);
    }

    /**
     * VNPay: thanh cong chi khi ca vnp_ResponseCode va vnp_TransactionStatus deu = 00.
     * Provider khong gui transactionStatus (MoMo) -> chi xet responseCode.
     */
    public boolean isSuccessCode() {
        if (!"00".equals(responseCode)) {
            return false;
        }
        return transactionStatus == null || transactionStatus.isBlank()
                || "00".equals(transactionStatus);
    }
}
