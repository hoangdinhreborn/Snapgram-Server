package com.example.notification.service;

import com.example.notification.entity.NotificationOutboxStatus;
import com.example.notification.entity.NotificationPushOutbox;
import com.example.notification.repository.NotificationPushOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationOutboxStateService {

    private final NotificationPushOutboxRepository outboxRepository;

    @Transactional
    public List<NotificationPushOutbox> claimBatch(int batchSize, Duration leaseDuration) {
        Instant now = Instant.now();
        List<NotificationPushOutbox> outboxItems = outboxRepository.findClaimable(
                NotificationOutboxStatus.PENDING,
                NotificationOutboxStatus.PROCESSING,
                now,
                PageRequest.of(0, batchSize));

        for (NotificationPushOutbox outbox : outboxItems) {
            outbox.setStatus(NotificationOutboxStatus.PROCESSING);
            outbox.setAttempts(outbox.getAttempts() + 1);
            outbox.setLeaseUntil(now.plus(leaseDuration));
        }
        return List.copyOf(outboxRepository.saveAll(outboxItems));
    }

    @Transactional
    public void markSent(UUID outboxId) {
        NotificationPushOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox row disappeared: " + outboxId));
        outbox.setStatus(NotificationOutboxStatus.SENT);
        outbox.setLeaseUntil(null);
        outbox.setLastError(null);
    }

    @Transactional
    public void markFailed(UUID outboxId, String error, int maxAttempts) {
        NotificationPushOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox row disappeared: " + outboxId));
        outbox.setLastError(error);
        outbox.setLeaseUntil(null);

        if (outbox.getAttempts() >= maxAttempts) {
            outbox.setStatus(NotificationOutboxStatus.FAILED);
            return;
        }

        long delaySeconds = Math.min(300, 1L << Math.min(outbox.getAttempts() - 1, 8));
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
    }
}
