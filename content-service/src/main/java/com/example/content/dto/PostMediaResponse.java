package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostMediaResponse {

    private String id;
    private String mediaId;
    private String mediaUrl;
    private String thumbnailUrl;
    private String mediaType;
    private int sortOrder;
    private Integer width;
    private Integer height;
    private Integer durationSec;
    private Instant createdAt;
}
