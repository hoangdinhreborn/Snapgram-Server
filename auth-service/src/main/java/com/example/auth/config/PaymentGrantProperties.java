package com.example.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Thoi han tang cho tung productCode sau thanh toan.
 * Cong thuc theo migration V6: verified_until = GREATEST(now, verified_until) + duration.
 * VD: payment.grants.BLUE_TICK=30d, doi duoc qua env PAYMENT_GRANT_BLUE_TICK.
 */
@Data
@Component
@ConfigurationProperties(prefix = "payment")
public class PaymentGrantProperties {

    /** productCode -> thoi han tang (VD: BLUE_TICK -> 30d). */
    private Map<String, Duration> grants = new HashMap<>();
}
