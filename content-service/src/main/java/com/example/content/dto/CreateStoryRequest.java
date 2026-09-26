package com.example.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateStoryRequest {
    @NotBlank(message = "mediaUrl is required")
    private String mediaUrl;

    private UUID mediaId;

    @NotNull(message = "mediaType is required")
    private String mediaType; // IMAGE or VIDEO

    private String caption;
    private String visibility = "FOLLOWERS";
}
