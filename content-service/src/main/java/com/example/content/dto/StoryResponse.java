package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryResponse {
    private String id;
    private String authorId;
    private String mediaId;
    private String mediaUrl;
    private String mediaType;
    private String caption;
    private String visibility;
    private boolean viewedByMe;
    private Instant createdAt;
    private Instant expiresAt;
}
