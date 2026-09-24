package com.example.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityStatusRequest {

    @NotNull(message = "showActivityStatus field is required")
    private Boolean showActivityStatus;
}
