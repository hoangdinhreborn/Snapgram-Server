package com.example.auth.dto;

import lombok.Data;

@Data
public class TwoFaDisableRequest {
    // Either totpCode OR backupCode must be provided (validated in service)
    private String totpCode;
    private String backupCode;
}
