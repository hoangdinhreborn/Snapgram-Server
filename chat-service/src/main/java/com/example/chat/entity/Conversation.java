package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String type; // "DIRECT" | "GROUP"

    @Column(length = 150)
    private String name;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "vanish_mode_enabled", nullable = false)
    @Builder.Default
    private boolean vanishModeEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
