package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_post_mentions")
@Data
public class PostMention {

    @EmbeddedId
    private PostMentionId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class PostMentionId implements Serializable {
        @Column(name = "post_id")
        private UUID postId;

        @Column(name = "mentioned_user_id")
        private UUID mentionedUserId;
    }
}
