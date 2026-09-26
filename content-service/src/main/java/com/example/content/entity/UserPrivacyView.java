package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_user_privacy_view")
@Data
public class UserPrivacyView {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
