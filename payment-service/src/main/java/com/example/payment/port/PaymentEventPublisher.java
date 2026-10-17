package com.example.payment.port;

import com.example.payment.entity.PaymentOrder;

public interface PaymentEventPublisher {
    void publishPaymentCompleted(PaymentOrder order);
}
