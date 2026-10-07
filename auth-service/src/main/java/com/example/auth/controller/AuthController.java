package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.security.CookieUtils;
import com.example.auth.security.JwtProperties;
import com.example.auth.security.SecurityUtils;
import com.example.auth.service.AuthService;
import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;

    // ─────────────────────────────────────────────────────────────
    // Register / Login / Refresh / Logout / Verify
    // ─────────────────────────────────────────────────────────────

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(
                response.getAccessToken(), jwtProperties.accessTokenTtl());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(
                response.getRefreshToken(), jwtProperties.refreshTokenTtl());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);

        if (Boolean.TRUE.equals(response.getRequiresTwoFa())) {
            return ResponseEntity.ok(response);
        }

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(
                response.getAccessToken(), jwtProperties.accessTokenTtl());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(
                response.getRefreshToken(), jwtProperties.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /** POST /api/auth/admin/login */
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.adminLogin(request);

        if (Boolean.TRUE.equals(response.getRequiresTwoFa())) {
            return ResponseEntity.ok(response);
        }

        ResponseCookie adminAccessCookie = cookieUtils.createAdminAccessTokenCookie(
                response.getAccessToken(), jwtProperties.adminAccessTokenTtl());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(
                response.getRefreshToken(), jwtProperties.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, adminAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            request = new RefreshTokenRequest();
        }

        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            cookieUtils.extractCookieValue(httpRequest, CookieUtils.REFRESH_TOKEN_COOKIE)
                    .ifPresent(request::setRefreshToken);
        }

        AuthResponse response = authService.refreshToken(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(
                response.getAccessToken(), jwtProperties.accessTokenTtl());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(
                response.getRefreshToken(), jwtProperties.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /** POST /api/auth/login/2fa */
    @PostMapping("/login/2fa")
    public ResponseEntity<AuthResponse> loginWith2Fa(@Valid @RequestBody TwoFaLoginRequest request) {
        AuthResponse response = authService.loginWith2Fa(request);

        ResponseCookie accessCookie = cookieUtils.createAccessTokenCookie(
                response.getAccessToken(), jwtProperties.accessTokenTtl());
        ResponseCookie refreshCookie = cookieUtils.createRefreshTokenCookie(
                response.getRefreshToken(), jwtProperties.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        if (request == null) {
            request = new RefreshTokenRequest();
        }

        if (request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            cookieUtils.extractCookieValue(httpRequest, CookieUtils.REFRESH_TOKEN_COOKIE)
                    .ifPresent(request::setRefreshToken);
        }

        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        } else {
            accessToken = cookieUtils.extractCookieValue(httpRequest, CookieUtils.ADMIN_ACCESS_TOKEN_COOKIE)
                    .orElseGet(() -> cookieUtils.extractCookieValue(httpRequest, CookieUtils.ACCESS_TOKEN_COOKIE).orElse(null));
        }

        authService.logout(request, accessToken);

        ResponseCookie clearAccess = cookieUtils.clearCookie(CookieUtils.ACCESS_TOKEN_COOKIE, "/");
        ResponseCookie clearAdminAccess = cookieUtils.clearCookie(CookieUtils.ADMIN_ACCESS_TOKEN_COOKIE, "/");
        ResponseCookie clearRefresh = cookieUtils.clearCookie(CookieUtils.REFRESH_TOKEN_COOKIE, "/api/auth");

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearAdminAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .build();
    }

    /** GET /api/auth/me */
    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(authService.getCurrentUser(currentUserId));
    }

    /** POST /api/auth/verify */
    @PostMapping("/verify")
    public ResponseEntity<Boolean> verify(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer ")) return ResponseEntity.ok(false);
        return ResponseEntity.ok(authService.verifyToken(authorization.substring(7)));
    }

    /** GET /api/auth/user */
    @GetMapping("/user")
    public ResponseEntity<AuthResponse.UserDto> getUserInfo(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(authService.getUserFromToken(authorization.substring(7)));
    }

    // ─────────────────────────────────────────────────────────────
    // Password Reset (public — no auth required)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/auth/forgot-password
     * Request a password reset email. Always returns 202 (anti-enumeration).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        String ip = resolveClientIp(httpRequest);
        passwordResetService.requestReset(request, ip);
        return ResponseEntity.accepted().build();
    }

    /**
     * POST /api/auth/reset-password
     * Confirm token + set new password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Email Verification (public — token sent via email)
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/auth/verify-email?token=xxx
     * Confirm email via link clicked in the email.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
