package com.example.payment.port;

import com.example.payment.dto.PaymentCallbackResult;

import java.time.Instant;
import java.util.Map;

public interface PaymentGateway {

    /** Ma provider luu vao payment_orders.provider (VNPAY, MOMO). */
    String provider();

    /** Tra ve URL thanh toan de redirect user sang provider. */
    String createPayment(CreatePaymentCommand command);

    PaymentCallbackResult verifyCallback(Map<String, String> params);

    /** Doi chieu giao dich voi provider — admin reconcile khi IPN that lac. */
    QueryResult queryTransaction(QueryCommand command);

    /** IPN ack voi format provider yeu cau: VNPay = RspCode, MoMo = resultCode. */
    Map<String, String> ipnAck(IpnOutcome outcome);

    record CreatePaymentCommand(
            String orderId,
            long amount,
            String orderInfo,
            String clientIp,
            Instant createDate,
            Instant expireDate
    ) {
    }

    record QueryCommand(String orderId, String orderInfo, Instant createDate) {
    }

    /**
     * Ket qua doi chieu da chuan hoa giua provider.
     * amount da quy ve VND (VNPay tra x100, MoMo tra VND).
     */
    record QueryResult(boolean found, boolean success, String responseCode, String transactionNo,
                       String channel, Instant payDate, long amount, String message) {
    }

    /** Ket qua xu ly IPN — moi provider chuyen thanh ack rieng. */
    enum IpnOutcome {
        HANDLED, ORDER_NOT_FOUND, INVALID_AMOUNT, ALREADY_CONFIRMED, INVALID_SIGNATURE
    }
}
