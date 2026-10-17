package com.example.payment.dto;

import java.time.Instant;

public record PaymentUrl(String url, Instant expireDate) {
}
