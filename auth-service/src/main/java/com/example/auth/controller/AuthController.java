package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.service.AuthService;
import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    // ─────────────────────────────────────────────────────────────
    // Register / Login / Refresh / Logout / Verify
    // ─────────────────────────────────────────────────────────────

    /** POST /api/auth/register */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** POST /api/auth/refresh */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    /** POST /api/auth/login/2fa */
    @PostMapping("/login/2fa")
    public ResponseEntity<AuthResponse> loginWith2Fa(@Valid @RequestBody TwoFaLoginRequest request) {
        return ResponseEntity.ok(authService.loginWith2Fa(request));
    }

    /** POST /api/auth/logout */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }
        authService.logout(request, accessToken);
        return ResponseEntity.noContent().build();
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
