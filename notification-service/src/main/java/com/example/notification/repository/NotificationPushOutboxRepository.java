package com.example.notification.repository;

import com.example.notification.entity.NotificationOutboxStatus;
import com.example.notification.entity.NotificationPushOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationPushOutboxRepository extends JpaRepository<NotificationPushOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox
            FROM NotificationPushOutbox outbox
            WHERE (outbox.status = :pending AND outbox.nextAttemptAt <= :now)
               OR (outbox.status = :processing AND outbox.leaseUntil <= :now)
            ORDER BY outbox.createdAt ASC
            """)
    List<NotificationPushOutbox> findClaimable(
            @Param("pending") NotificationOutboxStatus pending,
            @Param("processing") NotificationOutboxStatus processing,
            @Param("now") Instant now,
            Pageable pageable);
}