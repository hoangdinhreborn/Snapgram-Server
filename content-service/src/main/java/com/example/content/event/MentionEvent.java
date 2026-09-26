package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MentionEvent {
    private String mentionedUserId;
    private String postId;
    private String authorId;
    private Instant createdAt;
}
