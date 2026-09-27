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
public class ContentInteractionEvent {
    private String userId;
    private String targetUserId;
    private String postId;
    private String type;
    private Double watchTimeRatio;
    private Instant createdAt;
}
