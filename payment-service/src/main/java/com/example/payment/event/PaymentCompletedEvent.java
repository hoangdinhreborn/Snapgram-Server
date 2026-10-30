package com.example.payment.event;

import com.example.payment.entity.PaymentOrder;

import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        String eventId,
        UUID paymentId,
        String orderId,
        UUID userId,
        String productCode,
        long amount,
        String currency,
        String provider,
        String transactionId,
        Instant occurredAt
) {
    public static PaymentCompletedEvent from(PaymentOrder order) {
        return new PaymentCompletedEvent(
                order.getCompletedEventId(),
                order.getId(),
                order.getOrderId(),
                order.getUserId(),
                order.getProductCode(),
                order.getAmount(),
                order.getCurrency(),
                order.getProvider(),
                order.getProviderTransactionNo(),
                Instant.now());
    }
}
