package com.example.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    @Size(max = 500, message = "Website must not exceed 500 characters")
    @Pattern(
        regexp = "^$|^https?://.+",
        message = "Website must be a valid URL starting with http:// or https://"
    )
    private String website;

    @Size(max = 50, message = "Pronouns must not exceed 50 characters")
    private String pronouns;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;
}
