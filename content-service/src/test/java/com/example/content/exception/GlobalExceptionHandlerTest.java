package com.example.content.exception;

import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException for enum targetType should return 400 BAD_REQUEST with allowed values")
    void handleTypeMismatch_EnumTargetType_ShouldReturnBadRequestWithAllowedValues() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "user", ReportTargetType.class, "targetType", null, new IllegalArgumentException("No enum constant"));

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage())
                .contains("Invalid value 'user' for parameter 'targetType'")
                .contains("POST")
                .contains("COMMENT")
                .contains("USER");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException for enum status should return 400 BAD_REQUEST with allowed values")
    void handleTypeMismatch_EnumStatus_ShouldReturnBadRequestWithAllowedValues() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "BOGUS", ReportStatus.class, "status", null, new IllegalArgumentException("No enum constant"));

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage())
                .contains("Invalid value 'BOGUS' for parameter 'status'")
                .contains("PENDING")
                .contains("REVIEWED")
                .contains("RESOLVED")
                .contains("DISMISSED");
    }

    @Test
    @DisplayName("ContentNotFoundException should return 404 CONTENT_NOT_FOUND")
    void handleNotFound_ShouldReturn404() {
        ContentNotFoundException ex = new ContentNotFoundException("Report not found: 123");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("CONTENT_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Report not found: 123");
    }

    @Test
    @DisplayName("InvalidStatusTransitionException should return 409 INVALID_STATUS_TRANSITION")
    void handleInvalidStatusTransition_ShouldReturn409() {
        InvalidStatusTransitionException ex = new InvalidStatusTransitionException("Dismissed report cannot be resolved");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidStatusTransition(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_STATUS_TRANSITION");
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo("Dismissed report cannot be resolved");
    }

    @Test
    @DisplayName("IllegalArgumentException should return 400 BAD_REQUEST")
    void handleIllegalArgument_ShouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("adminId must not be null");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("adminId must not be null");
    }
}
