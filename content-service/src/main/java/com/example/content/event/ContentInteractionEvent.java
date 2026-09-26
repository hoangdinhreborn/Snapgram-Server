package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContentInteractionEvent {
    private String userId;
    private String postId;
    private String type;
    private Double watchTimeRatio;
    private Instant createdAt;
}
