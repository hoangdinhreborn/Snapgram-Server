package com.example.media.exception;

import com.example.media.ai.AiServiceUnavailableException;
import com.example.media.ai.AiTimeoutException;
import com.example.media.ai.RateLimitExceededException;
import com.example.media.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(MediaNotFoundException ex) {
        log.warn("Media not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "MEDIA_NOT_FOUND", "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(InvalidMediaException.class)
    public ResponseEntity<ErrorResponse> handleInvalidMedia(InvalidMediaException ex) {
        log.warn("Invalid media: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "INVALID_MEDIA", "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(403, "ACCESS_DENIED", "Forbidden", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "UNAUTHORIZED", "Unauthorized", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponse.of(413, "PAYLOAD_TOO_LARGE", "Payload Too Large", "Uploaded file exceeds maximum allowed limit (50MB)"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .code("VALIDATION_FAILED")
                .error("Validation Failed")
                .message("Request contains invalid parameters")
                .errors(fieldErrors)
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        log.warn("AI Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Too Many Requests", ex.getMessage()));
    }

    @ExceptionHandler(AiTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAiTimeout(AiTimeoutException ex) {
        log.warn("AI Gateway Timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(ErrorResponse.of(504, "AI_TIMEOUT", "Gateway Timeout", ex.getMessage()));
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(AiServiceUnavailableException ex) {
        log.warn("AI Service Unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(503, "AI_SERVICE_UNAVAILABLE", "Service Unavailable", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception in media-service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "INTERNAL_SERVER_ERROR", "Internal Server Error", "An unexpected error occurred"));
    }
}
