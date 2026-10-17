package com.example.payment.port;

import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.dto.PaymentUrl;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface PaymentGateway {

    PaymentUrl createPayment(CreatePaymentCommand command);

    PaymentCallbackResult verifyCallback(Map<String, String> params);

    record CreatePaymentCommand(
            UUID paymentId,
            String orderId,
            long amount,
            String orderInfo,
            String clientIp,
            Instant createDate,
            Instant expireDate
    ) {
    }
}
