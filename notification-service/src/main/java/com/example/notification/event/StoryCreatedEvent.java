package com.example.notification.event;

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
public class StoryCreatedEvent {
    private String storyId;
    private String authorId;
    private String mediaUrl;
    private String mediaType;
    private String visibility;
    private Instant expiresAt;
    private Instant createdAt;
    private List<String> followerIds;
}