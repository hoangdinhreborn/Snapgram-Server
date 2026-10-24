package com.example.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Đánh dấu người dùng đã đọc tới tin nhắn nào trong hội thoại. */
@Entity
@Table(name = "chat_conversation_read_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationReadStatus {

    @EmbeddedId
    private ReadStatusId id;

    @Column(name = "last_read_message_id", nullable = false)
    private UUID lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadStatusId implements Serializable {
        @Column(name = "conversation_id")
        private UUID conversationId;

        @Column(name = "user_id")
        private UUID userId;
    }
}
