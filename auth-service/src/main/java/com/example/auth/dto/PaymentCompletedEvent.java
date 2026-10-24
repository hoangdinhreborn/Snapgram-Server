package com.example.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Ban sao cua PaymentCompletedEvent ben payment-service (2 service rieng module
 * nen khong share duoc DTO — ten field phai khop JSON ma payment-service publish).
 */
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
}
