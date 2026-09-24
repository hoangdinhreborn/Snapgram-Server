package com.example.auth.controller;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshTokenRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.TwoFaLoginRequest;
import com.example.auth.service.AuthService;
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

    /**
     * Register new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login with email and password
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Complete login when 2FA is enabled
     * POST /api/auth/login/2fa
     */
    @PostMapping("/login/2fa")
    public ResponseEntity<AuthResponse> loginWith2Fa(@Valid @RequestBody TwoFaLoginRequest request) {
        AuthResponse response = authService.loginWith2Fa(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Logout (revoke refresh token + blacklist access token)
     * POST /api/auth/logout
     */
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

    /**
     * Verify token (for other services)
     * POST /api/auth/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<Boolean> verify(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.ok(false);
        }
        String token = authorization.substring(7);
        boolean valid = authService.verifyToken(token);
        return ResponseEntity.ok(valid);
    }

    /**
     * Get user info from token (for other services)
     * GET /api/auth/user
     */
    @GetMapping("/user")
    public ResponseEntity<AuthResponse.UserDto> getUserInfo(@RequestHeader("Authorization") String authorization) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authorization.substring(7);
        AuthResponse.UserDto userDto = authService.getUserFromToken(token);
        return ResponseEntity.ok(userDto);
    }
}
