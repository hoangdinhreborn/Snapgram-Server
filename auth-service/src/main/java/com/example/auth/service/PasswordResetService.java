package com.example.auth.service;

import com.example.auth.dto.ForgotPasswordRequest;
import com.example.auth.dto.ResetPasswordRequest;
import com.example.auth.entity.AuthPasswordReset;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.repository.AuthPasswordResetRepository;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;       // 256-bit → 43 chars base64url
    private static final long TTL_MINUTES = 15;

    private final AuthUserRepository userRepository;
    private final AuthPasswordResetRepository resetRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    // TODO: inject MailService when implemented
    // private final MailService mailService;

    // ─────────────────────────────────────────────────────────────
    // Step 1 — Request reset
    // ─────────────────────────────────────────────────────────────

    /**
     * Always returns 200 regardless of whether the email exists
     * to prevent user enumeration attacks.
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request, String ipAddress) {
        userRepository.findByEmail(request.getEmail().toLowerCase()).ifPresent(user -> {
            // Invalidate any previous active token for this user
            resetRepository.invalidateActiveTokens(user.getId(), Instant.now());

            // Generate new token
            String rawToken = generateToken();
            String tokenHash = sha256(rawToken);
            Instant expiresAt = Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES);

            AuthPasswordReset reset = new AuthPasswordReset();
            reset.setUserId(user.getId());
            reset.setTokenHash(tokenHash);
            reset.setExpiresAt(expiresAt);
            reset.setIpAddress(ipAddress);
            resetRepository.save(reset);

            // TODO: mailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            log.info("[DEV] Password reset token for {} → {} (expires {})", user.getEmail(), rawToken, expiresAt);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Step 2 — Confirm reset
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.getToken());

        AuthPasswordReset reset = resetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired reset token"));

        if (reset.getUsedAt() != null) {
            throw new InvalidCredentialsException("Reset token has already been used");
        }
        if (reset.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Reset token has expired");
        }

        AuthUser user = userRepository.findById(reset.getUserId())
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Mark token as used
        reset.setUsedAt(Instant.now());
        resetRepository.save(reset);

        // Revoke all active refresh tokens → force re-login everywhere
        revokeAllRefreshTokens(user.getId());

        log.info("Password reset successful for user {}", user.getUsername());
    }

    // ─────────────────────────────────────────────────────────────
    // Scheduled cleanup
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteExpiredTokens() {
        resetRepository.deleteExpired(Instant.now());
        log.debug("Expired password reset tokens cleaned up");
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void revokeAllRefreshTokens(UUID userId) {
        Instant now = Instant.now();
        refreshTokenRepository.findActiveByUserId(userId).forEach(token -> {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        });
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
