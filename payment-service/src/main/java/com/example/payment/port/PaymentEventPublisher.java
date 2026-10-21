package com.example.payment.port;

import com.example.payment.entity.PaymentOrder;

import java.util.concurrent.CompletableFuture;

public interface PaymentEventPublisher {
    /**
     * Gui event bat dong bo. Future hoan tat khi broker xac nhan (hoac loi)
     * de outbox dispatcher biet co can retry hay khong.
     */
    CompletableFuture<Void> publishPaymentCompleted(PaymentOrder order);
}
