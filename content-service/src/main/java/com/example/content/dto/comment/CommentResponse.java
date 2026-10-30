package com.example.content.dto.comment;

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
    /** Tác giả bình luận có Blue Tick còn hiệu lực hay không. */
    private Boolean authorVerified;
    private java.time.Instant authorVerifiedUntil;
    private String content;
    private String parentCommentId;
    /** URL ảnh/GIF, hoặc emoji nếu là sticker. */
    private String mediaUrl;
    /** IMAGE | GIF | STICKER */
    private String mediaType;
    private long likeCount;
    private boolean likedByMe;
    /** Số trả lời trực tiếp (chỉ có ở comment gốc) — null khi không áp dụng. */
    private Long replyCount;
    private Instant createdAt;
    private Instant updatedAt;
}
