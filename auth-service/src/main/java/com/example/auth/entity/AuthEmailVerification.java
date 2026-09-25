package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_email_verifications")
@Data
public class AuthEmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hash of the raw token sent to the user's email. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    /**
     * NULL  → verify the user's current email.
     * value → user requested an email change; this is the pending new address.
     */
    @Column(name = "new_email", length = 255)
    private String newEmail;

    /** Token expires 24 hours after creation. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is consumed (single-use). */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
