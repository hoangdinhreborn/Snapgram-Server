package com.example.auth.service;

import com.example.auth.dto.auth.AuthResponse;
import com.example.auth.dto.auth.LoginRequest;
import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import com.example.auth.exception.AccountLockedException;
import com.example.auth.exception.InvalidCredentialsException;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceLockTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TwoFaService twoFaService;

    @InjectMocks
    private AuthService authService;

    private AuthUser testUser;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        testUser = new AuthUser();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("hashed_password");
        testUser.setRole(Role.USER);
        testUser.setFailedLoginAttempts(0);
        testUser.setLockedUntil(null);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("wrong_password");
    }

    @Test
    @DisplayName("Attempt 1 & 2 with wrong password should increment counter and throw InvalidCredentialsException")
    void failedAttemptsBelowThreshold_ShouldThrowInvalidCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        // Attempt 1
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(testUser.getLockedUntil()).isNull();
        verify(userRepository, times(1)).save(testUser);

        // Attempt 2
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(2);
        assertThat(testUser.getLockedUntil()).isNull();
        verify(userRepository, times(2)).save(testUser);
    }

    @Test
    @DisplayName("Attempt 3 with wrong password should lock account for 15 minutes and throw AccountLockedException")
    void thirdFailedAttempt_ShouldLockAccountFor15Minutes() {
        testUser.setFailedLoginAttempts(2);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> {
                    AccountLockedException ale = (AccountLockedException) ex;
                    assertThat(ale.getRetryAfterSeconds()).isEqualTo(900L);
                });

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(testUser.getLockedUntil()).isNotNull();
        assertThat(testUser.getLockedUntil()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Subsequent login attempt while locked should throw AccountLockedException with remaining seconds")
    void loginWhileAccountIsLocked_ShouldThrowAccountLockedExceptionWithRemainingTime() {
        Instant lockExpiry = Instant.now().plus(Duration.ofMinutes(10));
        testUser.setLockedUntil(lockExpiry);
        testUser.setFailedLoginAttempts(3);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> {
                    AccountLockedException ale = (AccountLockedException) ex;
                    assertThat(ale.getRetryAfterSeconds()).isGreaterThan(550L).isLessThanOrEqualTo(600L);
                });

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Login after lock expires should clear lockout and reset counter")
    void loginAfterLockExpired_ShouldResetLockAndAllowLogin() {
        // Lock expired 1 minute ago
        testUser.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));
        testUser.setFailedLoginAttempts(3);

        loginRequest.setPassword("correct_password");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correct_password", "hashed_password")).thenReturn(true);
        when(roleService.getUserRoles(testUser.getId())).thenReturn(Collections.singletonList("USER"));
        when(roleService.getUserPermissions(testUser.getId())).thenReturn(Collections.emptyList());
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("mock_access_token");
        when(jwtService.getExpiration("mock_access_token")).thenReturn(Instant.now().plus(Duration.ofMinutes(15)));
        when(jwtService.generateRefreshToken(any())).thenReturn("mock_refresh_token");
        when(jwtService.getJti("mock_refresh_token")).thenReturn("mock_jti");
        when(jwtService.getExpiration("mock_refresh_token")).thenReturn(Instant.now().plus(Duration.ofDays(7)));

        AuthResponse response = authService.login(loginRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_access_token");
        assertThat(testUser.getLockedUntil()).isNull();
        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Admin login attempt 3 with wrong password should lock account and throw AccountLockedException")
    void adminLoginThirdFailedAttempt_ShouldLockAccount() {
        testUser.setRole(Role.ADMIN);
        testUser.setFailedLoginAttempts(2);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.adminLogin(loginRequest))
                .isInstanceOf(AccountLockedException.class)
                .satisfies(ex -> {
                    AccountLockedException ale = (AccountLockedException) ex;
                    assertThat(ale.getRetryAfterSeconds()).isEqualTo(900L);
                });

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(testUser.getLockedUntil()).isNotNull();
        verify(userRepository).save(testUser);
    }
}
