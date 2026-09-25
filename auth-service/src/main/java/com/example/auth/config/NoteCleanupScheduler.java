package com.example.auth.config;

import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.PasswordResetService;
import com.example.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoteCleanupScheduler {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    /** Delete expired notes every hour. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanupExpiredNotes() {
        log.debug("Running expired notes cleanup...");
        userService.deleteExpiredNotes();
    }

    /** Delete expired password reset tokens every hour. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanupExpiredPasswordResets() {
        log.debug("Running expired password reset tokens cleanup...");
        passwordResetService.deleteExpiredTokens();
    }

    /** Delete expired email verification tokens every hour. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanupExpiredEmailVerifications() {
        log.debug("Running expired email verification tokens cleanup...");
        emailVerificationService.deleteExpiredTokens();
    }
}
