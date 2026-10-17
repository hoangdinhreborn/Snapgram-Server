package com.example.payment.dto;

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
