package com.example.auth.dto;

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
    private String displayName;
    private String avatarUrl;
    private boolean emailVerified;
    private Instant createdAt;
}
