package com.example.notification.service;

import com.example.notification.entity.NotificationOutboxStatus;
import com.example.notification.entity.NotificationPushOutbox;
import com.example.notification.repository.NotificationPushOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxStateServiceTest {

    @Mock
    private NotificationPushOutboxRepository outboxRepository;

    @InjectMocks
    private NotificationOutboxStateService outboxStateService;

    @Test
    void claimBatchSetsLeaseAndIncrementsAttempt() {
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setAttempts(0);
        when(outboxRepository.findClaimable(eq(NotificationOutboxStatus.PENDING),
                eq(NotificationOutboxStatus.PROCESSING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(outbox));
        when(outboxRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<NotificationPushOutbox> claimed = outboxStateService.claimBatch(10, Duration.ofSeconds(30));

        assertThat(claimed).containsExactly(outbox);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PROCESSING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLeaseUntil()).isAfter(Instant.now());
    }

    @Test
    void failedAttemptSchedulesExponentialRetry() {
        UUID outboxId = UUID.randomUUID();
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.setId(outboxId);
        outbox.setAttempts(3);
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));
        Instant beforeFailure = Instant.now();

        outboxStateService.markFailed(outboxId, "temporary error", 8);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(outbox.getLastError()).isEqualTo("temporary error");
        assertThat(outbox.getLeaseUntil()).isNull();
        assertThat(outbox.getNextAttemptAt()).isAfterOrEqualTo(beforeFailure.plusSeconds(4));
    }

    @Test
    void failedAttemptExhaustingLimitMovesToFailed() {
        UUID outboxId = UUID.randomUUID();
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.setId(outboxId);
        outbox.setAttempts(4);
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        outboxStateService.markFailed(outboxId, "permanent error", 4);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(outbox.getLastError()).isEqualTo("permanent error");
    }

    @Test
    void markSentClearsLeaseAndError() {
        UUID outboxId = UUID.randomUUID();
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.setId(outboxId);
        outbox.setLeaseUntil(Instant.now().plusSeconds(10));
        outbox.setLastError("previous failure");
        when(outboxRepository.findById(outboxId)).thenReturn(Optional.of(outbox));

        outboxStateService.markSent(outboxId);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.SENT);
        assertThat(outbox.getLeaseUntil()).isNull();
        assertThat(outbox.getLastError()).isNull();
    }
}
