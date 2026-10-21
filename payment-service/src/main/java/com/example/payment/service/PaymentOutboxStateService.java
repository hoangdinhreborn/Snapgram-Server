package com.example.payment.service;

import com.example.payment.entity.PaymentOutbox;
import com.example.payment.entity.PaymentOutboxStatus;
import com.example.payment.repository.PaymentOutboxRepository;
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
public class PaymentOutboxStateService {

    private final PaymentOutboxRepository outboxRepository;

    /** Claim batch bang pessimistic lock + lease de nhieu instance khong gui trung. */
    @Transactional
    public List<PaymentOutbox> claimBatch(int batchSize, Duration leaseDuration) {
        Instant now = Instant.now();
        List<PaymentOutbox> items = outboxRepository.findClaimable(
                PaymentOutboxStatus.PENDING,
                PaymentOutboxStatus.PROCESSING,
                now,
                PageRequest.of(0, batchSize));

        for (PaymentOutbox item : items) {
            item.setStatus(PaymentOutboxStatus.PROCESSING);
            item.setAttempts(item.getAttempts() + 1);
            item.setLeaseUntil(now.plus(leaseDuration));
        }
        return List.copyOf(outboxRepository.saveAll(items));
    }

    @Transactional
    public void markSent(UUID outboxId) {
        PaymentOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox row disappeared: " + outboxId));
        outbox.setStatus(PaymentOutboxStatus.SENT);
        outbox.setLeaseUntil(null);
        outbox.setLastError(null);
        outbox.setSentAt(Instant.now());
    }

    @Transactional
    public void markFailed(UUID outboxId, String error, int maxAttempts) {
        PaymentOutbox outbox = outboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox row disappeared: " + outboxId));
        outbox.setLastError(error);
        outbox.setLeaseUntil(null);

        if (outbox.getAttempts() >= maxAttempts) {
            outbox.setStatus(PaymentOutboxStatus.FAILED);
            return;
        }

        long delaySeconds = Math.min(300, 1L << Math.min(outbox.getAttempts() - 1, 8));
        outbox.setStatus(PaymentOutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now().plusSeconds(delaySeconds));
    }
}
