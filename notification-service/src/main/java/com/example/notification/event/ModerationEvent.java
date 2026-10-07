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
public class ModerationEvent {
    private String reportId;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String status;
    private String moderatorId;
    private Instant createdAt;
}