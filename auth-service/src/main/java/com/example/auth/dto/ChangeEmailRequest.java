package com.example.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeEmailRequest {

    @NotBlank(message = "New email is required")
    @Email(message = "New email is invalid")
    private String newEmail;

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}
