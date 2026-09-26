package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StoryCreatedEvent {
    private String storyId;
    private String authorId;
    private String mediaUrl;
    private String mediaType;
    private String visibility;
    private Instant expiresAt;
    private Instant createdAt;
}
