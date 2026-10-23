package com.example.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
    private String id;
    private String type;
    private String name;
    private String avatarUrl;
    private List<String> memberIds;
    private MessageResponse lastMessage;
    private Instant updatedAt;
    private Instant createdAt;
}
