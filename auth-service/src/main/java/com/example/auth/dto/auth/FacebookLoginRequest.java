package com.example.auth.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacebookLoginRequest {

    /**
     * The authorization code received from Facebook OAuth dialog.
     */
    private String code;

    /**
     * The exact redirect URI used in the Facebook authorization dialog.
     */
    private String redirectUri;

    /**
     * Optional direct access token (e.g. from Facebook JS SDK).
     */
    private String accessToken;
}
