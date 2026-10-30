package com.example.payment.adapter.kafka;

import com.example.payment.event.PaymentCompletedEvent;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.port.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.kafka.topic-completed:payment.completed}")
    private String topicCompleted;

    @Override
    public CompletableFuture<Void> publishPaymentCompleted(PaymentOrder order) {
        PaymentCompletedEvent event = PaymentCompletedEvent.from(order);
        return kafkaTemplate.send(topicCompleted, order.getOrderId(), event)
                .thenApply(result -> (Void) null);
    }
}
