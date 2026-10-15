package com.example.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_push_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPushOutbox {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "notification_id", nullable = false, columnDefinition = "uuid")
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationOutboxStatus status;

    @Column(name = "grouped_update", nullable = false)
    private boolean groupedUpdate;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = NotificationOutboxStatus.PENDING;
        if (nextAttemptAt == null) nextAttemptAt = Instant.now();
        if (createdAt == null) createdAt = Instant.now();
    }
}