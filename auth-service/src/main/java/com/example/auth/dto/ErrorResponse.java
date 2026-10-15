package com.example.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String code;
    private String error;
    private String message;
    private List<FieldError> errors;
    private Instant timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }

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
}
