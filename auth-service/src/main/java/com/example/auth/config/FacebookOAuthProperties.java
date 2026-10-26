package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth.facebook")
public record FacebookOAuthProperties(
        String appId,
        String appSecret
) {
}
