package com.example.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PrivacySettingRequest {

    @NotNull(message = "privateAccount field is required")
    private Boolean privateAccount;
}
