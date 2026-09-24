package com.example.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BlockedUserResponse {
    private String userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private Instant blockedAt;
}
