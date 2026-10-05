package com.example.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SyncUserRolesRequest {

    @NotEmpty(message = "Roles list cannot be empty")
    private List<String> roles;
}
