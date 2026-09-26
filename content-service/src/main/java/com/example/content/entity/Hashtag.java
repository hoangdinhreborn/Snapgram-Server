package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_hashtags")
@Data
public class Hashtag {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String tag;

    @Column(name = "post_count", nullable = false)
    private long postCount = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
