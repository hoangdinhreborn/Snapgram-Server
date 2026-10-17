package com.example.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePaymentRequest(
        @NotBlank(message = "productCode is required")
        @Pattern(regexp = "BLUE_TICK", message = "Unknown product")
        String productCode
) {
}
