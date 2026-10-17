package com.example.payment.service;

import com.example.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachine {

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return switch (from) {
            case PENDING -> to == PaymentStatus.SUCCESS
                    || to == PaymentStatus.FAILED
                    || to == PaymentStatus.EXPIRED
                    || to == PaymentStatus.CANCELLED;
            case SUCCESS -> to == PaymentStatus.REFUNDED;
            default -> false;
        };
    }

    public void checkTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal payment transition: " + from + " -> " + to);
        }
    }
}
