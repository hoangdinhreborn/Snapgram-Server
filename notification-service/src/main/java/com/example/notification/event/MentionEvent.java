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
public class MentionEvent {
    private String mentionedUserId;
    private String postId;
    private String authorId;
    private Instant createdAt;
}