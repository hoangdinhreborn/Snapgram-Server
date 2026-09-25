package com.example.auth.service;

import com.example.auth.dto.ChangeEmailRequest;
import com.example.auth.entity.AuthEmailVerification;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.DuplicateUserException;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.repository.AuthEmailVerificationRepository;
import com.example.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int TOKEN_BYTES = 32;
    private static final long TTL_HOURS = 24;

    private final AuthUserRepository userRepository;
    private final AuthEmailVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    // TODO: inject MailService when implemented
    // private final MailService mailService;

    // ─────────────────────────────────────────────────────────────
    // Send verification email (current email)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void sendVerificationEmail(UUID userId) {
        AuthUser user = getUser(userId);

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }

        issueToken(user, null);
        log.info("Verification email requested for user {}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    // Verify token (confirm current email)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = PasswordResetService.sha256(rawToken);

        AuthEmailVerification record = verificationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired verification token"));

        validateToken(record);

        AuthUser user = getUser(record.getUserId());

        if (record.getNewEmail() == null) {
            // Simple email verification
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
        } else {
            // Email change confirmation
            if (userRepository.existsByEmail(record.getNewEmail())) {
                throw new DuplicateUserException("Email already registered: " + record.getNewEmail());
            }
            user.setEmail(record.getNewEmail());
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
        }

        userRepository.save(user);

        // Mark token as used
        record.setUsedAt(Instant.now());
        verificationRepository.save(record);

        log.info("Email verified for user {}", user.getId());
    }

    // ─────────────────────────────────────────────────────────────
    // Request email change
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void requestEmailChange(UUID userId, ChangeEmailRequest request) {
        AuthUser user = getUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        String newEmail = request.getNewEmail().toLowerCase();

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("New email must differ from the current email");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new DuplicateUserException("Email already registered: " + newEmail);
        }

        issueToken(user, newEmail);
        log.info("Email change requested for user {} → {}", userId, newEmail);
    }

    // ─────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteExpiredTokens() {
        verificationRepository.deleteExpired(Instant.now());
        log.debug("Expired email verification tokens cleaned up");
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void issueToken(AuthUser user, String newEmail) {
        // Invalidate any previous active token
        verificationRepository.invalidateActiveTokens(user.getId(), Instant.now());

        String rawToken = generateToken();
        String tokenHash = PasswordResetService.sha256(rawToken);
        Instant expiresAt = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);

        AuthEmailVerification record = new AuthEmailVerification();
        record.setUserId(user.getId());
        record.setTokenHash(tokenHash);
        record.setNewEmail(newEmail);
        record.setExpiresAt(expiresAt);
        verificationRepository.save(record);

        // TODO: mailService.sendVerificationEmail(newEmail != null ? newEmail : user.getEmail(), rawToken);
        log.info("[DEV] Email verification token for {} → {} (expires {})",
                newEmail != null ? newEmail : user.getEmail(), rawToken, expiresAt);
    }

    private void validateToken(AuthEmailVerification record) {
        if (record.getUsedAt() != null) {
            throw new InvalidCredentialsException("Verification token has already been used");
        }
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Verification token has expired");
        }
    }

    private AuthUser getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
