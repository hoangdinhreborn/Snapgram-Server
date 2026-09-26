package com.example.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMediaRequest {

    private UUID mediaId;

    @NotBlank(message = "mediaUrl is required")
    private String mediaUrl;

    private String thumbnailUrl;

    private String mediaType; // IMAGE or VIDEO

    private int sortOrder = 0;

    private Integer width;

    private Integer height;

    private Integer durationSec;
}
