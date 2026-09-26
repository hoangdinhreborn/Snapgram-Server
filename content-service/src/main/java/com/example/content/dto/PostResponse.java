package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {
    private String id;
    private String authorId;
    private String contentType;
    private String caption;
    private String mediaId;
    private String mediaUrl;
    private List<PostMediaResponse> mediaItems;
    private String tags;
    private long likeCount;
    private long commentCount;
    private long viewCount;
    private String status;
    private String visibility;
    private int editCount;
    private Instant editedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> hashtags;
    private List<String> mentionedUserIds;
    private boolean likedByMe;
}
