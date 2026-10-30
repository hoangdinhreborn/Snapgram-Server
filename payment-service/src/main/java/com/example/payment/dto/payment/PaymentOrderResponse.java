package com.example.payment.dto.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentOrderResponse(
        UUID id,
        String orderId,
        String productCode,
        long amount,
        String currency,
        String status,
        Instant createdAt,
        Instant expiresAt
) {
}
