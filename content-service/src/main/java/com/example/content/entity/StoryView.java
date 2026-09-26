package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_story_views")
@Data
public class StoryView {

    @EmbeddedId
    private StoryViewId id;

    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    @PrePersist
    protected void onCreate() {
        viewedAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class StoryViewId implements Serializable {
        @Column(name = "story_id")
        private UUID storyId;

        @Column(name = "viewer_id")
        private UUID viewerId;
    }
}
