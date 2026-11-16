package com.example.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.turnstile")
public record TurnstileProperties(
        String secret,
        List<String> allowedHostnames,
        String verifyUrl,
        boolean enabled,
        int timeoutSeconds
) {
    public TurnstileProperties {
        if (allowedHostnames == null || allowedHostnames.isEmpty()) {
            allowedHostnames = List.of("localhost", "127.0.0.1");
        }
        if (verifyUrl == null || verifyUrl.isBlank()) {
            verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 10;
        }
    }
}
