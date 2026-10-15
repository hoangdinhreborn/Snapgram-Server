package com.example.auth.config;

import com.example.auth.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    private ObjectMapper objectMapper;
    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        interceptor = new RateLimitInterceptor(proxyManager, objectMapper);
    }

    @Test
    @DisplayName("When rate limit is exceeded, preHandle should return false, HTTP 429, standard headers and ErrorResponse")
    @SuppressWarnings("unchecked")
    void preHandle_WhenRateLimitExceeded_ShouldReturn429WithHeadersAndBody() throws Exception {
        RemoteBucketBuilder<byte[]> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(any(), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getHeader("X-Rate-Limit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-Rate-Limit-Reset")).isNotNull();

        String body = response.getContentAsString();
        assertThat(body).isNotBlank();

        ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
        assertThat(errorResponse.getStatus()).isEqualTo(429);
        assertThat(errorResponse.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(errorResponse.getError()).isEqualTo("Too Many Requests");
        assertThat(errorResponse.getMessage()).isEqualTo("Rate limit exceeded. Please try again later.");
    }

    @Test
    @DisplayName("When rate limit is within threshold, preHandle should return true and attach rate limit headers")
    @SuppressWarnings("unchecked")
    void preHandle_WhenAllowed_ShouldReturnTrueAndSetHeaders() throws Exception {
        RemoteBucketBuilder<byte[]> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(any(), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(bucket.getAvailableTokens()).thenReturn(99L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Rate-Limit-Limit")).isEqualTo("100");
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("99");
        assertThat(response.getHeader("X-Rate-Limit-Reset")).isNotNull();
    }
}
