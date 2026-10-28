package com.example.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateAdminUserRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Email(message = "Email is invalid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    private Boolean emailVerified;

    private List<String> roles;
}
