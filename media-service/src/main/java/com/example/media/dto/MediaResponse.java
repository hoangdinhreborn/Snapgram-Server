package com.example.media.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaResponse {
    private String id;
    private String ownerId;
    private String url;
    private String thumbnailUrl;
    private String mimeType;
    private Long sizeBytes;
    private String status;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private Instant createdAt;
}
