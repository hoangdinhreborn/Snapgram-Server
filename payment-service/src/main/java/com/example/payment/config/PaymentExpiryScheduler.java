package com.example.payment.config;

import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryScheduler {

    private final PaymentService paymentService;

    /** Quet don PENDING qua han moi phut (don timeout 15 phut). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireOverduePayments() {
        try {
            int expired = paymentService.expireOverduePayments();
            if (expired > 0) {
                log.info("Expired {} overdue payment orders", expired);
            }
        } catch (Exception e) {
            log.error("Failed to expire overdue payments", e);
        }
    }
}
