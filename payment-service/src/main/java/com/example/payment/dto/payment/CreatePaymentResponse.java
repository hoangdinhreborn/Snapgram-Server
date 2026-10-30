package com.example.payment.dto.payment;

import java.time.Instant;

public record CreatePaymentResponse(String orderId, String paymentUrl, String status, Instant expiresAt) {
}
