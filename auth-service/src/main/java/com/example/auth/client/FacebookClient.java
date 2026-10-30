package com.example.auth.client;

import com.example.auth.config.FacebookOAuthProperties;
import com.example.auth.dto.auth.FacebookTokenResponse;
import com.example.auth.dto.auth.FacebookUserProfile;
import com.example.auth.exception.OAuthAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

@Component
@Slf4j
public class FacebookClient {

    private static final String GRAPH_API_BASE_URL = "https://graph.facebook.com/v20.0";

    private final FacebookOAuthProperties properties;
    private final RestClient restClient;

    @Autowired
    public FacebookClient(FacebookOAuthProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // Constructor for testing / mocking
    public FacebookClient(FacebookOAuthProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /**
     * Exchange Facebook OAuth authorization code for an access token.
     */
    public String exchangeCodeForAccessToken(String code, String redirectUri) {
        if (properties.appId() == null || properties.appId().isBlank()
                || properties.appSecret() == null || properties.appSecret().isBlank()) {
            throw new OAuthAuthenticationException("Facebook App credentials are not configured on the server.");
        }

        URI uri = UriComponentsBuilder.fromUriString(GRAPH_API_BASE_URL + "/oauth/access_token")
                .queryParam("client_id", properties.appId())
                .queryParam("client_secret", properties.appSecret())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code)
                .build()
                .toUri();

        try {
            log.info("Exchanging Facebook authorization code for access token...");
            FacebookTokenResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(FacebookTokenResponse.class);

            if (response == null || response.getAccessToken() == null) {
                throw new OAuthAuthenticationException("Failed to obtain access token from Facebook.");
            }
            return response.getAccessToken();
        } catch (RestClientResponseException ex) {
            log.error("Facebook token exchange failed. Status: {}, Response: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OAuthAuthenticationException("Xác thực mã Facebook thất bại: " + ex.getStatusText(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Network error while connecting to Facebook Graph API: {}", ex.getMessage());
            throw new OAuthAuthenticationException("Không thể kết nối đến máy chủ Facebook. Vui lòng thử lại sau.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during Facebook token exchange", ex);
            throw new OAuthAuthenticationException("Lỗi đăng nhập Facebook: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieve Facebook user profile with id, name, email and picture.
     */
    public FacebookUserProfile getUserProfile(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new OAuthAuthenticationException("Facebook access token is missing.");
        }

        URI uri = UriComponentsBuilder.fromUriString(GRAPH_API_BASE_URL + "/me")
                .queryParam("fields", "id,name,email,picture.width(400).height(400)")
                .queryParam("access_token", accessToken)
                .build()
                .toUri();

        try {
            log.info("Fetching Facebook user profile...");
            FacebookUserProfile profile = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(FacebookUserProfile.class);

            if (profile == null || profile.getId() == null) {
                throw new OAuthAuthenticationException("Facebook did not return valid user profile information.");
            }
            return profile;
        } catch (RestClientResponseException ex) {
            log.error("Facebook user profile fetch failed. Status: {}, Response: {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OAuthAuthenticationException("Không thể lấy thông tin tài khoản Facebook: " + ex.getStatusText(), ex);
        } catch (ResourceAccessException ex) {
            log.error("Network error while fetching Facebook user profile: {}", ex.getMessage());
            throw new OAuthAuthenticationException("Không thể kết nối đến máy chủ Facebook. Vui lòng thử lại sau.", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while fetching Facebook user profile", ex);
            throw new OAuthAuthenticationException("Lỗi lấy thông tin Facebook: " + ex.getMessage(), ex);
        }
    }
}
