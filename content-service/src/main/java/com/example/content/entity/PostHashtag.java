package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_post_hashtags")
@Data
public class PostHashtag {

    @EmbeddedId
    private PostHashtagId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class PostHashtagId implements Serializable {
        @Column(name = "post_id")
        private UUID postId;

        @Column(name = "hashtag_id")
        private UUID hashtagId;
    }
}
