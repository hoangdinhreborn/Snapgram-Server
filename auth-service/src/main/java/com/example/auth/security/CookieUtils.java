package com.example.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String ADMIN_ACCESS_TOKEN_COOKIE = "admin_access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final boolean secureCookie;

    public CookieUtils(@Value("${app.cookie.secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public ResponseCookie createAccessTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie createAdminAccessTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from(ADMIN_ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path(path)
                .maxAge(0)
                .build();
    }

    public Optional<String> extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
