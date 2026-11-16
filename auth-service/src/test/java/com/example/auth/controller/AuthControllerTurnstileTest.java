package com.example.auth.controller;

import com.example.auth.dto.auth.AuthResponse;
import com.example.auth.dto.auth.LoginRequest;
import com.example.auth.dto.auth.RegisterRequest;
import com.example.auth.exception.GlobalExceptionHandler;
import com.example.auth.exception.InvalidTurnstileException;
import com.example.auth.security.CookieUtils;
import com.example.auth.security.JwtProperties;
import com.example.auth.service.AuthService;
import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.PasswordResetService;
import com.example.auth.service.TurnstileVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTurnstileTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private CookieUtils cookieUtils;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private TurnstileVerificationService turnstileVerificationService;

    @Mock
    private HttpServletRequest servletRequest;

    @InjectMocks
    private AuthController authController;

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("register delegates Turnstile token with 'signup' action")
    void register_DelegatesTurnstileTokenWithSignupAction() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@snapgram.com");
        request.setPassword("password123!");
        request.setTurnstileToken("turnstile-signup-token");

        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.50");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        when(authService.register(any())).thenReturn(authResponse);
        when(jwtProperties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(cookieUtils.createAccessTokenCookie(any(), any())).thenReturn(ResponseCookie.from("access_token", "val").build());
        when(cookieUtils.createRefreshTokenCookie(any(), any())).thenReturn(ResponseCookie.from("refresh_token", "val").build());

        ResponseEntity<AuthResponse> response = authController.register(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(turnstileVerificationService, times(1))
                .verify(eq("turnstile-signup-token"), eq("signup"), eq("192.168.1.50"));
    }

    @Test
    @DisplayName("login delegates Turnstile token with 'login' action")
    void login_DelegatesTurnstileTokenWithLoginAction() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@snapgram.com");
        request.setPassword("password123!");
        request.setTurnstileToken("turnstile-login-token");

        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
        when(authService.login(any())).thenReturn(authResponse);
        when(jwtProperties.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(cookieUtils.createAccessTokenCookie(any(), any())).thenReturn(ResponseCookie.from("access_token", "val").build());
        when(cookieUtils.createRefreshTokenCookie(any(), any())).thenReturn(ResponseCookie.from("refresh_token", "val").build());

        ResponseEntity<AuthResponse> response = authController.login(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(turnstileVerificationService, times(1))
                .verify(eq("turnstile-login-token"), eq("login"), eq("127.0.0.1"));
    }

    @Test
    @DisplayName("adminLogin delegates Turnstile token with 'admin_login' action")
    void adminLogin_DelegatesTurnstileTokenWithAdminLoginAction() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@snapgram.com");
        request.setPassword("test-admin-password");
        request.setTurnstileToken("turnstile-admin-token");

        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("admin-access-token")
                .refreshToken("admin-refresh-token")
                .build();
        when(authService.adminLogin(any())).thenReturn(authResponse);
        when(jwtProperties.adminAccessTokenTtl()).thenReturn(Duration.ofMinutes(30));
        when(jwtProperties.refreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(cookieUtils.createAdminAccessTokenCookie(any(), any())).thenReturn(ResponseCookie.from("admin_access_token", "val").build());
        when(cookieUtils.createRefreshTokenCookie(any(), any())).thenReturn(ResponseCookie.from("refresh_token", "val").build());

        ResponseEntity<AuthResponse> response = authController.adminLogin(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(turnstileVerificationService, times(1))
                .verify(eq("turnstile-admin-token"), eq("admin_login"), eq("10.0.0.1"));
    }

    @Test
    @DisplayName("When Turnstile verification throws InvalidTurnstileException, GlobalExceptionHandler returns 403 Forbidden with BOT_VERIFICATION_FAILED")
    void turnstileVerificationFailure_Returns403Forbidden() {
        InvalidTurnstileException ex = new InvalidTurnstileException("Security check failed: action mismatch.");

        var errorResponse = exceptionHandler.handleInvalidTurnstile(ex);

        assertThat(errorResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(errorResponse.getBody()).isNotNull();
        assertThat(errorResponse.getBody().getStatus()).isEqualTo(403);
        assertThat(errorResponse.getBody().getCode()).isEqualTo("BOT_VERIFICATION_FAILED");
        assertThat(errorResponse.getBody().getError()).isEqualTo("Forbidden");
        assertThat(errorResponse.getBody().getMessage()).isEqualTo("Security check failed: action mismatch.");
    }
}
