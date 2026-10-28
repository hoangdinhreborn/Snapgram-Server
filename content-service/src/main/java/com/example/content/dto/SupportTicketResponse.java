package com.example.content.dto;

import com.example.content.entity.SupportTicketCategory;
import com.example.content.entity.SupportTicketPriority;
import com.example.content.entity.SupportTicketStatus;
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
public class SupportTicketResponse {

    private String id;
    private String userId;
    private String userUsername;
    private String userDisplayName;
    private String userAvatarUrl;
    private SupportTicketCategory category;
    private String subject;
    private String description;
    private List<String> attachments;
    private SupportTicketStatus status;
    private SupportTicketPriority priority;
    private String adminReply;
    private String responderId;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
