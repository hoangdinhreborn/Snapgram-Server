package com.example.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class CreateCommentRequest {
    @NotBlank(message = "content is required")
    @Size(max = 2000, message = "Comment max 2000 characters")
    private String content;

    private UUID parentCommentId;
}
