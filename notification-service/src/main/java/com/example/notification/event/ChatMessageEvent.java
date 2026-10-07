package com.example.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent {
    private String senderId;
    private String receiverId;
    private String conversationId;
    private String messageId;
    private Boolean muted;
    private Instant createdAt;
}