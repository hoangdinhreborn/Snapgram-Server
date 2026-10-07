package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 50, message = "Role name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Role name must contain only uppercase letters, numbers, and underscores")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    private List<UUID> permissionIds;
}
