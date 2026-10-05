package com.example.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration adminAccessTokenTtl,
        Duration refreshTokenTtl
) {
    public Duration adminAccessTokenTtl() {
        return adminAccessTokenTtl != null ? adminAccessTokenTtl : Duration.ofMinutes(30);
    }
}