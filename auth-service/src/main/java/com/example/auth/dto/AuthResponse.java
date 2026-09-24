package com.example.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserDto user;

    // 2FA pending flow
    private Boolean requiresTwoFa;
    private String tempToken;

    @Data
    @Builder
    public static class UserDto {
        private String id;
        private String username;
        private String email;
        private String displayName;
    }
}
