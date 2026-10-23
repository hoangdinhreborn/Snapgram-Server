package com.example.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_conversation_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "MEMBER";

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean muted = false;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (joinedAt == null) joinedAt = Instant.now();
    }
}
