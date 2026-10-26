package com.example.payment.entity;

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

/**
 * Outbox cho event payment.completed. Ghi cung transaction voi payment_orders
 * de dam bao "da thanh toan" va "da phat event" khong bi lech nhau.
 */
@Entity
@Table(name = "payment_outbox")
@Getter
@Setter
@NoArgsConstructor
public class PaymentOutbox {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOutboxStatus status;

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

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = PaymentOutboxStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
