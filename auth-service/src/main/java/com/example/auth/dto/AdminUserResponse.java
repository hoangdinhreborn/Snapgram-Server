package com.example.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {

    private UUID id;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;

    private boolean privateAccount;
    private boolean emailVerified;
    private boolean twoFaEnabled;

    private boolean banned;
    private Instant bannedAt;
    private String banReason;

    private Instant lastSeenAt;
    private Instant createdAt;

    private String role;
    private Collection<String> roles;
}
