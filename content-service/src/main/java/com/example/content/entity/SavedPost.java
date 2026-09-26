package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_saved_posts")
@Data
public class SavedPost {

    @EmbeddedId
    private SavedPostId id;

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    @PrePersist
    protected void onCreate() {
        savedAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class SavedPostId implements Serializable {
        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "post_id")
        private UUID postId;
    }
}
