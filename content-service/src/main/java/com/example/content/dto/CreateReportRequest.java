package com.example.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateReportRequest {
    @NotNull(message = "targetType is required")
    private String targetType; // POST, COMMENT, USER

    @NotNull(message = "targetId is required")
    private UUID targetId;

    @NotBlank(message = "reason is required")
    private String reason;

    private String description;
}
