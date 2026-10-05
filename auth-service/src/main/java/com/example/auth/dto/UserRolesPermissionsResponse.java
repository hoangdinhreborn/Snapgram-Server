package com.example.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserRolesPermissionsResponse {
    private UUID userId;
    private String primaryRole;
    private List<String> roles;
    private List<String> effectivePermissions;
}
