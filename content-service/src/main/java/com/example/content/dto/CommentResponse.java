package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {
    private String id;
    private String postId;
    private String authorId;
    private String content;
    private String parentCommentId;
    private long likeCount;
    private boolean likedByMe;
    private Instant createdAt;
    private Instant updatedAt;
}
