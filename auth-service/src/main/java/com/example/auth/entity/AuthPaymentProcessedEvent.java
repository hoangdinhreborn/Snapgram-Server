package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency cho consumer {@code payment.completed}.
 * eventId do payment-service sinh 1 lan khi payment chuyen SUCCESS.
 */
@Entity
@Table(name = "auth_payment_processed_events")
@Data
public class AuthPaymentProcessedEvent {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(name = "granted_until", nullable = false)
    private Instant grantedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
