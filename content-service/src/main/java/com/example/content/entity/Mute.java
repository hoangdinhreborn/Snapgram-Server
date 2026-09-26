package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_mutes")
@Data
public class Mute {

    @Id
    private UUID id;

    @Column(name = "muter_id", nullable = false)
    private UUID muterId;

    @Column(name = "muted_id", nullable = false)
    private UUID mutedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
