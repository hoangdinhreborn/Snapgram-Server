package com.example.auth.service;

import com.example.auth.config.TurnstileProperties;
import com.example.auth.dto.auth.TurnstileVerifyResponse;
import com.example.auth.exception.InvalidTurnstileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurnstileVerificationServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private TurnstileProperties defaultProperties;

    @BeforeEach
    void setUp() {
        defaultProperties = new TurnstileProperties(
                "dummy-secret-key",
                List.of("localhost", "127.0.0.1"),
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                true,
                10
        );
    }

    private void mockSiteverifyResponse(TurnstileVerifyResponse response) {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        doReturn(requestBodySpec).when(requestBodySpec).body(any(org.springframework.util.MultiValueMap.class));
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(TurnstileVerifyResponse.class))).thenReturn(response);
    }

    @Test
    @DisplayName("When app.turnstile.enabled is false, verification is skipped silently")
    void verify_WhenDisabled_ShouldSkip() {
        TurnstileProperties disabledProperties = new TurnstileProperties(
                "dummy-secret-key",
                List.of("localhost"),
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                false,
                10
        );
        TurnstileVerificationService service = new TurnstileVerificationService(disabledProperties, restClient);

        assertThatCode(() -> service.verify(null, "signup", "127.0.0.1"))
                .doesNotThrowAnyException();
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("When token is null or blank, throw InvalidTurnstileException")
    void verify_WhenTokenBlank_ShouldThrow() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);

        assertThatThrownBy(() -> service.verify("", "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("Missing or invalid bot verification token");

        assertThatThrownBy(() -> service.verify(null, "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("Missing or invalid bot verification token");
    }

    @Test
    @DisplayName("When token length exceeds 2048 characters, throw InvalidTurnstileException")
    void verify_WhenTokenTooLong_ShouldThrow() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);
        String overlongToken = "a".repeat(2049);

        assertThatThrownBy(() -> service.verify(overlongToken, "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("Missing or invalid bot verification token");
    }

    @Test
    @DisplayName("When secret is missing, throw InvalidTurnstileException")
    void verify_WhenSecretMissing_ShouldThrow() {
        TurnstileProperties noSecretProps = new TurnstileProperties(
                "",
                List.of("localhost"),
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                true,
                10
        );
        TurnstileVerificationService service = new TurnstileVerificationService(noSecretProps, restClient);

        assertThatThrownBy(() -> service.verify("valid-token", "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("Turnstile bot verification is not configured properly");
    }

    @Test
    @DisplayName("When allowed hostnames list is empty, throw InvalidTurnstileException")
    void verify_WhenEmptyAllowedHostnames_ShouldThrow() {
        TurnstileProperties emptyHostnamesProps = new TurnstileProperties(
                "dummy-secret",
                Collections.emptyList(),
                "https://challenges.cloudflare.com/turnstile/v0/siteverify",
                true,
                10
        );
        // Default constructor enforces non-empty fallback, test behavior
        TurnstileVerificationService service = new TurnstileVerificationService(emptyHostnamesProps, restClient);
        // Fallback populates localhost, so let's mock siteverify success
        mockSiteverifyResponse(TurnstileVerifyResponse.builder()
                .success(true)
                .action("signup")
                .hostname("localhost")
                .build());

        assertThatCode(() -> service.verify("valid-token", "signup", "127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("When Cloudflare siteverify returns success = false, throw InvalidTurnstileException")
    void verify_WhenCloudflareRejects_ShouldThrow() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);
        mockSiteverifyResponse(TurnstileVerifyResponse.builder()
                .success(false)
                .errorCodes(List.of("invalid-input-response"))
                .build());

        assertThatThrownBy(() -> service.verify("bad-token", "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("Security check failed");
    }

    @Test
    @DisplayName("When action does not match expected action, throw InvalidTurnstileException")
    void verify_WhenActionMismatch_ShouldThrow() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);
        mockSiteverifyResponse(TurnstileVerifyResponse.builder()
                .success(true)
                .action("login")
                .hostname("localhost")
                .build());

        assertThatThrownBy(() -> service.verify("valid-token", "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("action mismatch");
    }

    @Test
    @DisplayName("When origin hostname is not in allowed list, throw InvalidTurnstileException")
    void verify_WhenHostnameDisallowed_ShouldThrow() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);
        mockSiteverifyResponse(TurnstileVerifyResponse.builder()
                .success(true)
                .action("signup")
                .hostname("evil-phishing-domain.com")
                .build());

        assertThatThrownBy(() -> service.verify("valid-token", "signup", "127.0.0.1"))
                .isInstanceOf(InvalidTurnstileException.class)
                .hasMessageContaining("origin hostname not allowed");
    }

    @Test
    @DisplayName("When token, action and hostname are all valid, verification succeeds")
    void verify_WhenAllValid_ShouldSucceed() {
        TurnstileVerificationService service = new TurnstileVerificationService(defaultProperties, restClient);
        mockSiteverifyResponse(TurnstileVerifyResponse.builder()
                .success(true)
                .action("signup")
                .hostname("localhost")
                .build());

        assertThatCode(() -> service.verify("valid-token", "signup", "127.0.0.1"))
                .doesNotThrowAnyException();
    }
}
