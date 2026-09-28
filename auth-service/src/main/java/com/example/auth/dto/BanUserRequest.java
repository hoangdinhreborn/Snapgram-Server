package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BanUserRequest {

    @NotBlank(message = "Ban reason is required")
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;
}
