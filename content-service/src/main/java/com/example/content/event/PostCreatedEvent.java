package com.example.content.event;

import lombok.*;
import java.time.Instant;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PostCreatedEvent {
    private String postId;
    private String authorId;
    private String contentType;
    private String visibility;
    private String mediaId;
    private String mediaUrl;
    private List<String> mediaUrls;
    private List<String> hashtags;
    private List<String> mentionedUserIds;
    private Instant createdAt;
}
