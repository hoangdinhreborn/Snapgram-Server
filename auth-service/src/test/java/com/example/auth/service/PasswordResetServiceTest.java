package com.example.auth.service;

import com.example.auth.dto.ForgotPasswordRequest;
import com.example.auth.dto.ResetPasswordRequest;
import com.example.auth.entity.AuthPasswordReset;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.repository.AuthPasswordResetRepository;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private AuthPasswordResetRepository resetRepository;

    @Mock
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private AuthUser testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new AuthUser();
        testUser.setId(userId);
        testUser.setEmail("user@snapgram.com");
        testUser.setUsername("snapuser");
        testUser.setPasswordHash("hashed_old_password");
    }

    @Test
    @DisplayName("requestReset when user exists should save reset record and send email via MailService")
    void requestReset_WhenUserExists_ShouldSaveRecordAndSendMail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@snapgram.com");

        when(userRepository.findByEmail("user@snapgram.com")).thenReturn(Optional.of(testUser));

        passwordResetService.requestReset(request, "127.0.0.1");

        verify(resetRepository).invalidateActiveTokens(eq(userId), any(Instant.class));
        verify(resetRepository).save(any(AuthPasswordReset.class));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetEmail(eq("user@snapgram.com"), tokenCaptor.capture());

        assertThat(tokenCaptor.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("requestReset when user does not exist should do nothing (anti-enumeration)")
    void requestReset_WhenUserDoesNotExist_ShouldDoNothing() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown@snapgram.com");

        when(userRepository.findByEmail("unknown@snapgram.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset(request, "127.0.0.1");

        verify(resetRepository, never()).invalidateActiveTokens(any(), any());
        verify(resetRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    @DisplayName("resetPassword when token is valid should update password hash and revoke refresh tokens")
    void resetPassword_WhenValidToken_ShouldSucceed() {
        String rawToken = "sample-valid-token";
        String tokenHash = PasswordResetService.sha256(rawToken);

        AuthPasswordReset reset = new AuthPasswordReset();
        reset.setId(UUID.randomUUID());
        reset.setUserId(userId);
        reset.setTokenHash(tokenHash);
        reset.setExpiresAt(Instant.now().plusSeconds(600));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(rawToken);
        request.setNewPassword("NewSecurePassword123!");

        when(resetRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(reset));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("NewSecurePassword123!", "hashed_old_password")).thenReturn(false);
        when(passwordEncoder.encode("NewSecurePassword123!")).thenReturn("hashed_new_password");
        when(refreshTokenRepository.findActiveByUserId(userId)).thenReturn(java.util.Collections.emptyList());

        passwordResetService.resetPassword(request);

        assertThat(reset.getUsedAt()).isNotNull();
        assertThat(testUser.getPasswordHash()).isEqualTo("hashed_new_password");
        verify(refreshTokenRepository).findActiveByUserId(userId);
    }

    @Test
    @DisplayName("resetPassword when new password equals current password should throw exception")
    void resetPassword_WhenNewPasswordEqualsCurrent_ShouldThrowException() {
        String rawToken = "sample-valid-token";
        String tokenHash = PasswordResetService.sha256(rawToken);

        AuthPasswordReset reset = new AuthPasswordReset();
        reset.setId(UUID.randomUUID());
        reset.setUserId(userId);
        reset.setTokenHash(tokenHash);
        reset.setExpiresAt(Instant.now().plusSeconds(600));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(rawToken);
        request.setNewPassword("SamePassword123!");

        when(resetRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(reset));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("SamePassword123!", "hashed_old_password")).thenReturn(true);

        assertThatThrownBy(() -> passwordResetService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ from the current password");

        verify(refreshTokenRepository, never()).findActiveByUserId(any());
    }
}
