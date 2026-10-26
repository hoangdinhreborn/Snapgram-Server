package com.example.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentInteractionEvent {
    private String eventId;
    private String userId;
    private String targetUserId;
    private String postId;
    private String type;
    private Double watchTimeRatio;
    private Instant createdAt;
}
