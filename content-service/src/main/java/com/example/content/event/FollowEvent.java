package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FollowEvent {
    private String followerId;
    private String followingId;
    private String status;
    private Instant createdAt;
}
