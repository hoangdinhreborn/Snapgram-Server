package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_comment_likes")
@Data
public class CommentLike {

    @EmbeddedId
    private CommentLikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class CommentLikeId implements Serializable {
        @Column(name = "comment_id")
        private UUID commentId;

        @Column(name = "user_id")
        private UUID userId;
    }
}
