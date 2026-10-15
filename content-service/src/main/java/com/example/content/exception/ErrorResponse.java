package com.example.content.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String code;
    private String error;
    private String message;
    private Instant timestamp;
    private List<FieldError> errors;

    public static ErrorResponse of(int status, String error, String message) {
        return of(status, null, error, message);
    }

    public static ErrorResponse of(int status, String code, String error, String message) {
        return ErrorResponse.builder()
                .status(status)
                .code(code)
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
