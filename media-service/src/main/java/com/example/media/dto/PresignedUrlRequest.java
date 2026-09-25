package com.example.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PresignedUrlRequest {

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotBlank(message = "mimeType is required")
    private String mimeType;

    @NotNull(message = "sizeBytes is required")
    @Positive(message = "sizeBytes must be positive")
    private Long sizeBytes;
}
