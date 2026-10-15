package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationPushOutbox;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOutboxDispatcher {

    private final NotificationOutboxStateService outboxStateService;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryService notificationDeliveryService;

    @Value("${notification.outbox.batch-size:50}")
    private int batchSize;

    @Value("${notification.outbox.lease-seconds:60}")
    private long leaseSeconds;

    @Value("${notification.outbox.max-attempts:8}")
    private int maxAttempts;

    @Scheduled(fixedDelayString = "${notification.outbox.poll-interval-ms:1000}")
    public void dispatchPending() {
        List<NotificationPushOutbox> batch = outboxStateService.claimBatch(batchSize, Duration.ofSeconds(leaseSeconds));
        for (NotificationPushOutbox outbox : batch) {
            try {
                Notification notification = notificationRepository.findById(outbox.getNotificationId())
                        .orElseThrow(() -> new IllegalStateException("Notification missing for outbox " + outbox.getId()));
                if (outbox.isGroupedUpdate()) {
                    notificationDeliveryService.deliverGroupedUpdate(notification);
                } else {
                    notificationDeliveryService.deliver(notification);
                }
                outboxStateService.markSent(outbox.getId());
            } catch (Exception e) {
                log.error("Push outbox delivery failed id={} attempt={}", outbox.getId(), outbox.getAttempts(), e);
                outboxStateService.markFailed(outbox.getId(), e.getMessage(), maxAttempts);
            }
        }
    }
}