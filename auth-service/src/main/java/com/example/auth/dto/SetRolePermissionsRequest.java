package com.example.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SetRolePermissionsRequest {

    @NotNull(message = "Permission IDs list is required")
    private List<UUID> permissionIds;
}
