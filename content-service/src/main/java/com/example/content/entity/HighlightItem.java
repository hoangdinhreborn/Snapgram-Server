package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_highlight_items")
@Data
public class HighlightItem {

    @EmbeddedId
    private HighlightItemId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class HighlightItemId implements Serializable {
        @Column(name = "highlight_id")
        private UUID highlightId;

        @Column(name = "story_id")
        private UUID storyId;
    }
}
