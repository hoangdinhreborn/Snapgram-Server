package com.example.chat.dto.conversation;

import com.example.chat.dto.message.MessageResponse;
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
    /** Người đối thoại (chỉ có ở hội thoại DIRECT) — đã kèm username/avatar/verified. */
    private PartnerResponse partner;
    private MessageResponse lastMessage;
    private long unreadCount;
    private String lastReadMessageId;
    private boolean muted;
    private Instant updatedAt;
    private Instant createdAt;
}
