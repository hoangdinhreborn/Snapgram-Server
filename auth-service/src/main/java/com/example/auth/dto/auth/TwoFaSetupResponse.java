package com.example.auth.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TwoFaSetupResponse {
    private String secret;
    private String otpAuthUrl;
    private String qrCodeBase64;
}
