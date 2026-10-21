package com.example.payment.service;

import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentOutbox;
import com.example.payment.port.PaymentEventPublisher;
import com.example.payment.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gui event payment.completed tu outbox. Kafka loi -> retry voi backoff;
 * qua so lan -> FAILED (khong mat row, con doi soat duoc).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxDispatcher {

    private final PaymentOutboxStateService outboxStateService;
    private final PaymentOrderRepository orderRepository;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.outbox.batch-size:50}")
    private int batchSize;

    @Value("${payment.outbox.lease-seconds:60}")
    private long leaseSeconds;

    @Value("${payment.outbox.max-attempts:10}")
    private int maxAttempts;

    @Value("${payment.outbox.send-timeout-seconds:10}")
    private long sendTimeoutSeconds;

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:2000}")
    public void dispatchPending() {
        List<PaymentOutbox> batch = outboxStateService.claimBatch(batchSize, Duration.ofSeconds(leaseSeconds));
        for (PaymentOutbox outbox : batch) {
            try {
                PaymentOrder order = orderRepository.findByOrderId(outbox.getOrderId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Order missing for outbox " + outbox.getId()));
                eventPublisher.publishPaymentCompleted(order)
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                outboxStateService.markSent(outbox.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                outboxStateService.markFailed(outbox.getId(), "interrupted", maxAttempts);
                return;
            } catch (Exception e) {
                log.error("payment.completed outbox failed id={} orderId={} attempt={}",
                        outbox.getId(), outbox.getOrderId(), outbox.getAttempts(), e);
                outboxStateService.markFailed(outbox.getId(), e.getMessage(), maxAttempts);
            }
        }
    }
}
