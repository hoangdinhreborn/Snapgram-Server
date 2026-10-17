package com.example.payment.adapter.kafka;

import com.example.payment.dto.PaymentCompletedEvent;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.port.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${payment.kafka.topic-completed:payment.completed}")
    private String topicCompleted;

    @Override
    public void publishPaymentCompleted(PaymentOrder order) {
        PaymentCompletedEvent event = PaymentCompletedEvent.from(order);
        kafkaTemplate.send(topicCompleted, order.getOrderId(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment.completed orderId={}", order.getOrderId(), ex);
                    }
                });
    }
}
