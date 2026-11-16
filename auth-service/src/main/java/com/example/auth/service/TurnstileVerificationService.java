package com.example.auth.service;

import com.example.auth.config.TurnstileProperties;
import com.example.auth.dto.auth.TurnstileVerifyResponse;
import com.example.auth.exception.InvalidTurnstileException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class TurnstileVerificationService {

    private final TurnstileProperties properties;
    private final RestClient restClient;
    private final Set<String> expectedHostnames;

    @Autowired
    public TurnstileVerificationService(TurnstileProperties properties) {
        this(properties, createDefaultRestClient(properties));
    }

    public TurnstileVerificationService(TurnstileProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
        this.expectedHostnames = properties.allowedHostnames() != null
                ? new HashSet<>(properties.allowedHostnames())
                : Set.of("localhost", "127.0.0.1");
    }

    private static RestClient createDefaultRestClient(TurnstileProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, properties.timeoutSeconds()) * 1000;
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public void verify(String token, String expectedAction, String clientIp) {
        if (!properties.enabled()) {
            log.debug("Turnstile verification skipped: app.turnstile.enabled is false");
            return;
        }

        if (token == null || token.isBlank() || token.length() > 2048) {
            log.warn("turnstile_rejected reason=missing_or_invalid_length token_length={}", token != null ? token.length() : 0);
            throw new InvalidTurnstileException("Missing or invalid bot verification token");
        }

        if (properties.secret() == null || properties.secret().isBlank()) {
            log.error("turnstile_misconfigured reason=missing_turnstile_secret");
            throw new InvalidTurnstileException("Turnstile bot verification is not configured properly on the server");
        }

        if (expectedHostnames.isEmpty()) {
            log.error("turnstile_misconfigured reason=empty_expected_hostnames");
            throw new InvalidTurnstileException("No allowed hostnames configured for bot verification");
        }

        TurnstileVerifyResponse response;
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("secret", properties.secret());
            formData.add("response", token);
            if (clientIp != null && !clientIp.isBlank()) {
                formData.add("remoteip", clientIp);
            }

            response = restClient.post()
                    .uri(properties.verifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TurnstileVerifyResponse.class);
        } catch (Exception e) {
            log.error("turnstile_siteverify_error message={}", e.getMessage(), e);
            throw new InvalidTurnstileException("Failed to verify security challenge with Cloudflare Turnstile", e);
        }

        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            log.warn("turnstile_rejected reason=verification_failed errors={}",
                    response != null ? response.getErrorCodes() : "null_response");
            throw new InvalidTurnstileException("Security check failed. Please refresh and try again.");
        }

        boolean isTestingKey = response.getMetadata() != null
                && Boolean.TRUE.equals(response.getMetadata().getResultWithTestingKey());
        if (!isTestingKey) {
            if (expectedAction != null && !expectedAction.equals(response.getAction())) {
                log.warn("turnstile_rejected reason=action_mismatch expected={} actual={}",
                        expectedAction, response.getAction());
                throw new InvalidTurnstileException("Security check failed: action mismatch.");
            }

            if (response.getHostname() == null || !expectedHostnames.contains(response.getHostname())) {
                log.warn("turnstile_rejected reason=hostname_disallowed hostname={} allowed={}",
                        response.getHostname(), expectedHostnames);
                throw new InvalidTurnstileException("Security check failed: origin hostname not allowed.");
            }
        } else {
            log.debug("turnstile_test_key_accepted hostname={}", response.getHostname());
        }

        log.debug("turnstile_verified action={} hostname={}", response.getAction(), response.getHostname());
    }
}
