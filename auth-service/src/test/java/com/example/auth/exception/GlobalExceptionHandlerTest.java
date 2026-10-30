package com.example.auth.exception;

import com.example.auth.dto.common.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should return 423 Locked with ACCOUNT_LOCKED code and Retry-After header")
    void shouldReturn423WithRetryAfterHeader() {
        long retryAfterSeconds = 900L;
        String errorMessage = "Account is temporarily locked due to too many failed attempts. Please try again later.";
        AccountLockedException ex = new AccountLockedException(errorMessage, retryAfterSeconds);

        ResponseEntity<ErrorResponse> response = handler.handleAccountLocked(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("900");

        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(423);
        assertThat(body.getCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(body.getError()).isEqualTo("Locked");
        assertThat(body.getMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Should return 401 Unauthorized with INVALID_CREDENTIALS code")
    void shouldReturn401WithInvalidCredentialsCode() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Invalid email or password");

        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.getError()).isEqualTo("Unauthorized");
    }

    @Test
    @DisplayName("Should return 403 Forbidden with USER_BANNED code")
    void shouldReturn403WithUserBannedCode() {
        AccountBannedException ex = new AccountBannedException("Account has been banned");

        ResponseEntity<ErrorResponse> response = handler.handleAccountBanned(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getCode()).isEqualTo("USER_BANNED");
        assertThat(body.getError()).isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("Should return 404 Not Found with USER_NOT_FOUND code")
    void shouldReturn404WithUserNotFoundCode() {
        UserNotFoundException ex = new UserNotFoundException("User not found");

        ResponseEntity<ErrorResponse> response = handler.handleUserNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("Should return 409 Conflict with DUPLICATE_USER code")
    void shouldReturn409WithDuplicateUserCode() {
        DuplicateUserException ex = new DuplicateUserException("Username already exists");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getCode()).isEqualTo("DUPLICATE_USER");
    }

    @Test
    @DisplayName("Should return 400 Bad Request with BAD_REQUEST code for IllegalArgumentException")
    void shouldReturn400ForIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid status 'UNKNOWN'. Allowed values: [ACTIVE, BANNED, PENDING]");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArg(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(body.getMessage()).contains("Allowed values: [ACTIVE, BANNED, PENDING]");
    }

    @Test
    @DisplayName("Should return 400 Bad Request with BAD_REQUEST code for MethodArgumentTypeMismatchException")
    void shouldReturn400ForTypeMismatch() {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "INVALID_ROLE", com.example.auth.entity.Role.class, "role", null, new IllegalArgumentException());

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getCode()).isEqualTo("BAD_REQUEST");
        assertThat(body.getMessage())
                .contains("Invalid value 'INVALID_ROLE' for parameter 'role'")
                .contains("USER")
                .contains("ADMIN");
    }
}
