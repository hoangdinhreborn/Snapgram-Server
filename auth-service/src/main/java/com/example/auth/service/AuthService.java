package com.example.auth.service;

import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RefreshTokenRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.entity.AuthRefreshToken;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.DuplicateUserException;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Register new user with USER role
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering user: {}", request.getUsername());

        // Validate input
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        // Check for duplicates
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("Email already registered: " + request.getEmail());
        }

        // Create user
        AuthUser user = new AuthUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername());
        user.setPrivateAccount(false);
        user.setTwoFaEnabled(false);
        user.setEmailVerified(false);
        user.setShowActivityStatus(true);

        userRepository.save(user);
        log.info("User registered: {} ({})", user.getUsername(), user.getId());

        roleService.assignRole(user.getId(), "USER");

        return generateTokens(user);

    }

    /**
     * Login with email and password
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt: {}", request.getEmail());

        // Validate input
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }

        // Find user
        AuthUser user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found for email {}", request.getEmail());
                    return new InvalidCredentialsException("Invalid email or password");
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user {}", user.getUsername());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        log.info("User logged in: {}", user.getUsername());

        // Generate tokens
        return generateTokens(user);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        try {
            // Validate refresh token
            if (!jwtService.isRefreshToken(request.getRefreshToken())) {
                throw new InvalidCredentialsException("Invalid refresh token");
            }

            // Get user ID from token
            UUID userId = jwtService.getUserId(request.getRefreshToken());
            String jti = jwtService.getJti(request.getRefreshToken());

            // Check if token is not revoked
            AuthRefreshToken storedToken = refreshTokenRepository.findByJti(jti)
                    .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found"));

            if (storedToken.getRevokedAt() != null) {
                log.warn("Attempt to use revoked refresh token: {}", jti);
                throw new InvalidCredentialsException("Refresh token has been revoked");
            }

            if (storedToken.getExpiresAt().isBefore(Instant.now())) {
                log.warn("Refresh token expired: {}", jti);
                throw new InvalidCredentialsException("Refresh token has expired");
            }

            // Get user
            AuthUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));

            log.info("Token refreshed for user: {}", user.getUsername());

            // Revoke the old refresh token (rotation) to prevent reuse
            storedToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(storedToken);

            // Generate new tokens
            return generateTokens(user);

        } catch (JwtException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid refresh token");
        }
    }

    /**
     * Logout by revoking refresh token and blacklisting access token in Redis
     */
    @Transactional
    public void logout(RefreshTokenRequest request, String accessToken) {
        log.info("Logout request");

        // 1. Revoke refresh token in DB
        if (request.getRefreshToken() != null && !request.getRefreshToken().isEmpty()) {
            try {
                if (!jwtService.isRefreshToken(request.getRefreshToken())) {
                    log.warn("Logout attempted with non-refresh token, ignoring");
                } else {
                    String jti = jwtService.getJti(request.getRefreshToken());
                    refreshTokenRepository.findByJti(jti).ifPresent(token -> {
                        token.setRevokedAt(Instant.now());
                        refreshTokenRepository.save(token);
                        log.info("Refresh token revoked: {}", jti);
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to revoke refresh token: {}", e.getMessage());
            }
        }

        // 2. Blacklist access token in Redis so it cannot be reused
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                if (jwtService.isAccessToken(accessToken)) {
                    String jti = jwtService.getJti(accessToken);
                    long ttlSeconds = jwtService.getExpiration(accessToken).getEpochSecond()
                            - Instant.now().getEpochSecond();
                    tokenBlacklistService.blacklist(jti, ttlSeconds);
                    log.info("Access token blacklisted for jti={}", jti);
                }
            } catch (Exception e) {
                // Don't fail logout because of blacklist errors
                log.warn("Failed to blacklist access token: {}", e.getMessage());
            }
        }
    }

    /**
     * Verify access token (for other services)
     */
    @Transactional(readOnly = true)
    public boolean verifyToken(String token) {
        try {
            if (!jwtService.isAccessToken(token)) {
                return false;
            }
            jwtService.parseToken(token);
            // Check if the token has been blacklisted (user logged out)
            String jti = jwtService.getJti(token);
            if (tokenBlacklistService.isBlacklisted(jti)) {
                log.debug("Token verification failed: token has been blacklisted jti={}", jti);
                return false;
            }
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get user info from access token
     */
    @Transactional(readOnly = true)
    public AuthResponse.UserDto getUserFromToken(String token) {
        try {
            UUID userId = jwtService.getUserId(token);
            AuthUser user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidCredentialsException("User not found"));

            return buildUserDto(user);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException("Invalid token");
        }
    }

    /**
     * Generate access and refresh tokens
     */
    private AuthResponse generateTokens(AuthUser user) {
        UUID userId = user.getId();
        String username = user.getUsername();
        String email = user.getEmail();
        String displayName = user.getDisplayName();

        Collection<String> roles = roleService.getUserRoles(user.getId());

        // Generate access token
        String accessToken = jwtService.generateAccessToken(user, roles);
        Instant accessTokenExpiration = jwtService.getExpiration(accessToken);

        // Generate refresh token
        String refreshToken = jwtService.generateRefreshToken(user);
        String refreshTokenJti = jwtService.getJti(refreshToken);
        Instant refreshTokenExpiration = jwtService.getExpiration(refreshToken);

        // Store refresh token in database
        AuthRefreshToken storedToken = new AuthRefreshToken();
        storedToken.setUserId(user.getId());
        storedToken.setJti(refreshTokenJti);
        storedToken.setTokenHash(hashToken(refreshToken));
        storedToken.setExpiresAt(refreshTokenExpiration);
        storedToken.setCreatedAt(Instant.now());

        refreshTokenRepository.save(storedToken);

        // Calculate expires_in (seconds)
        long expiresIn = (accessTokenExpiration.getEpochSecond() - Instant.now().getEpochSecond());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(buildUserDto(userId, username, email, displayName))
                .build();
    }

    private AuthResponse.UserDto buildUserDto(UUID userId, String username, String email, String displayName) {
        return AuthResponse.UserDto.builder()
                .id(userId.toString())
                .username(username)
                .email(email)
                .displayName(displayName)
                .build();
    }

    private AuthResponse.UserDto buildUserDto(AuthUser user) {
        return buildUserDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName());
    }

    /**
     * Hash refresh token for storage (SHA-256)
     */
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
