package com.example.auth.controller;

import com.example.auth.security.CookieUtils;
import com.example.auth.security.JwtProperties;
import com.example.auth.service.AuthService;
import com.example.auth.service.EmailVerificationService;
import com.example.auth.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerVerifyTest {

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

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("Verify with missing Authorization header should return 200 OK with false")
    void verify_MissingAuthorizationHeader_ShouldReturnFalse() {
        ResponseEntity<Boolean> response = authController.verify(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Verify with malformed Authorization header (no Bearer prefix) should return 200 OK with false")
    void verify_MalformedAuthorizationHeader_ShouldReturnFalse() {
        ResponseEntity<Boolean> response = authController.verify("not-a-jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Verify with invalid or expired token should return 200 OK with false")
    void verify_InvalidToken_ShouldReturnFalse() {
        when(authService.verifyToken("invalid_jwt")).thenReturn(false);

        ResponseEntity<Boolean> response = authController.verify("Bearer invalid_jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
        verify(authService).verifyToken("invalid_jwt");
    }

    @Test
    @DisplayName("Verify with valid token should return 200 OK with true")
    void verify_ValidToken_ShouldReturnTrue() {
        when(authService.verifyToken("valid_jwt")).thenReturn(true);

        ResponseEntity<Boolean> response = authController.verify("Bearer valid_jwt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();
        verify(authService).verifyToken("valid_jwt");
    }
}
