package com.example.auth.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TwoFaEnableResponse {
    private List<String> backupCodes;
    private String message;
}
