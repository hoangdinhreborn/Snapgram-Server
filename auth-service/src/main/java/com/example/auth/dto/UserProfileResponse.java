package com.example.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    // Always visible
    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean privateAccount;
    private Instant createdAt;

    // Hidden when viewer is not the owner AND account is private
    private String bio;
    private String website;
    private String pronouns;
    private String category;

    // Only visible when showActivityStatus = true
    private Instant lastSeenAt;

    // Only visible to account owner
    private String email;
    private boolean emailVerified;
    private boolean twoFaEnabled;
    private boolean showActivityStatus;
}
