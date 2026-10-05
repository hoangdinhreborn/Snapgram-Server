package com.example.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoleDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private boolean isSystem;
    private List<PermissionResponse> permissions;
    private long userCount;
    private Instant createdAt;
    private Instant updatedAt;
}
