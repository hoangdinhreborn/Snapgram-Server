package com.example.payment.port;

import java.time.Duration;

public record ProductPrice(String productCode, long amount, String currency, Duration duration) {
}
