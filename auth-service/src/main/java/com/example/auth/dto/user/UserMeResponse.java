package com.example.auth.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserMeResponse {
    private String id;
    private String username;
    private String email;
    private String role;
    private java.util.Collection<String> roles;
    private java.util.Collection<String> permissions;
    private String displayName;
    private String avatarUrl;
    private boolean emailVerified;
    private boolean verified;
    private Instant verifiedUntil;
    private Instant createdAt;
}
