package com.example.content.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_close_friends")
@Data
public class CloseFriend {

    @EmbeddedId
    private CloseFriendId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Embeddable
    @Data
    public static class CloseFriendId implements Serializable {
        @Column(name = "owner_id")
        private UUID ownerId;

        @Column(name = "friend_id")
        private UUID friendId;
    }
}
