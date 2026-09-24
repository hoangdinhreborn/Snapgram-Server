package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TwoFaLoginRequest {

    @NotBlank(message = "Temp token is required")
    private String tempToken;

    // Either totpCode OR backupCode must be provided (validated in service)
    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be exactly 6 digits")
    private String totpCode;

    private String backupCode;
}
