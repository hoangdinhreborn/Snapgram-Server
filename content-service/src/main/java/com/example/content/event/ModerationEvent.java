package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ModerationEvent {
    private String reportId;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private Instant createdAt;
}
