package com.example.gateway.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private Instant timestamp;

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
