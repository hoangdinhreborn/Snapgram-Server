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
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String content;
    private String parentCommentId;
    private long likeCount;
    private boolean likedByMe;
    /** Số trả lời trực tiếp (chỉ có ở comment gốc) — null khi không áp dụng. */
    private Long replyCount;
    private Instant createdAt;
    private Instant updatedAt;
}
