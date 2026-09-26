package com.example.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCollectionRequest {
    @NotBlank(message = "name is required")
    @Size(max = 100)
    private String name;

    private boolean isPrivate = true;
}
