package com.example.content.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class InteractionRequest {
    @NotNull(message = "type is required")
    private String type; // LIKE, VIEW, SHARE, SAVE, SKIP

    @DecimalMin("0.0") @DecimalMax("1.0")
    private BigDecimal watchTimeRatio;

    @DecimalMin("0.0") @DecimalMax("10.0")
    private BigDecimal explicitRating;
}
