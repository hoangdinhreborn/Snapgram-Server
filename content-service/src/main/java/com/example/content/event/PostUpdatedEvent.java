package com.example.content.event;

import lombok.*;
import java.time.Instant;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PostUpdatedEvent {
    private String postId;
    private String authorId;
    private String caption;
    private String visibility;
    private Instant updatedAt;
}
