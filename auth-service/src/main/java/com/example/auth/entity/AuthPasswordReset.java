package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_password_resets")
@Data
public class AuthPasswordReset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hash of the raw token sent to the user's email. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    /** Token expires 15 minutes after creation. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is consumed (single-use). */
    @Column(name = "used_at")
    private Instant usedAt;

    /** IPv4 or IPv6 of the requester for audit / abuse detection. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
