package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NoteRequest {

    @NotBlank(message = "Note content is required")
    @Size(max = 60, message = "Note must not exceed 60 characters")
    private String content;
}
