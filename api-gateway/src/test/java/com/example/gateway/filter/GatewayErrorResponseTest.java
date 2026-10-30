package com.example.gateway.filter;

import com.example.gateway.dto.common.ErrorResponse;
import com.example.gateway.util.JwtPayloadUtil;
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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayErrorResponseTest {

    private ObjectMapper objectMapper;

    @Mock
    private WebClient authWebClient;

    @Mock
    private JwtPayloadUtil jwtPayloadUtil;

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private GatewayFilterChain filterChain;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("JwtAuthenticationFilter unauthorized response on missing token should match standard ErrorResponse (Shape A)")
    void jwtFilter_MissingToken_ShouldReturnStandardErrorResponse() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authWebClient, jwtPayloadUtil, objectMapper);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"invalid_token\"");

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertThat(responseBody).isNotNull();

        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        assertThat(errorResponse.getStatus()).isEqualTo(401);
        assertThat(errorResponse.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(errorResponse.getError()).isEqualTo("Unauthorized");
        assertThat(errorResponse.getMessage()).isEqualTo("Missing or invalid Authorization header");
        assertThat(errorResponse.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should return 401 when auth-service rejects token with 401 (not 503)")
    @SuppressWarnings("unchecked")
    void jwtFilter_WhenAuthServiceReturns401_ShouldReturn401Unauthorized() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authWebClient, jwtPayloadUtil, objectMapper);

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/auth/verify")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        WebClientResponseException ex = WebClientResponseException.create(
                401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.error(ex));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer error=\"invalid_token\"");

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertThat(responseBody).isNotNull();

        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        assertThat(errorResponse.getStatus()).isEqualTo(401);
        assertThat(errorResponse.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(errorResponse.getError()).isEqualTo("Unauthorized");
        assertThat(errorResponse.getMessage()).isEqualTo("Invalid or expired token");
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should return 503 only when auth-service is genuinely unavailable")
    @SuppressWarnings("unchecked")
    void jwtFilter_WhenAuthServiceIsDown_ShouldReturn503ServiceUnavailable() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authWebClient, jwtPayloadUtil, objectMapper);

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/auth/verify")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        WebClientRequestException ex = new WebClientRequestException(
                new ConnectException("Connection refused"),
                HttpMethod.POST,
                URI.create("/api/auth/verify"),
                HttpHeaders.EMPTY);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.error(ex));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-format-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertThat(responseBody).isNotNull();

        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        assertThat(errorResponse.getStatus()).isEqualTo(503);
        assertThat(errorResponse.getCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(errorResponse.getError()).isEqualTo("Service Unavailable");
        assertThat(errorResponse.getMessage()).isEqualTo("Authentication service unavailable");
    }

    @Test
    @DisplayName("RateLimitFilter exceeded response should match standard ErrorResponse with Retry-After header")
    @SuppressWarnings("unchecked")
    void rateLimitFilter_Exceeded_ShouldReturnStandardErrorResponseWithRetryAfter() throws Exception {
        RemoteBucketBuilder<byte[]> builder = mock(RemoteBucketBuilder.class);
        BucketProxy bucket = mock(BucketProxy.class);

        when(proxyManager.builder()).thenReturn(builder);
        when(builder.build(any(), any(java.util.function.Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false);

        RateLimitFilter filter = new RateLimitFilter(proxyManager, 100, 60, objectMapper);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/content/posts")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Rate-Limit-Limit")).isEqualTo("100");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Rate-Limit-Remaining")).isEqualTo("0");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Rate-Limit-Reset")).isNotNull();

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertThat(responseBody).isNotNull();

        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
        assertThat(errorResponse.getStatus()).isEqualTo(429);
        assertThat(errorResponse.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(errorResponse.getError()).isEqualTo("Too Many Requests");
        assertThat(errorResponse.getMessage()).isEqualTo("Rate limit exceeded. Please try again later.");
        assertThat(errorResponse.getTimestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should resolve admin_access_token cookie on shared endpoints like /api/auth/me")
    @SuppressWarnings("unchecked")
    void jwtFilter_AdminCookieOnSharedEndpoint_ShouldResolveAndVerify() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authWebClient, jwtPayloadUtil, objectMapper);

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/auth/verify")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer admin-token-abc"))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(true));

        Map<String, Object> claims = Map.of("sub", "admin-123");
        when(jwtPayloadUtil.extractPayload("admin-token-abc")).thenReturn(claims);
        when(jwtPayloadUtil.getUserId(claims)).thenReturn("admin-123");
        when(jwtPayloadUtil.getUsername(claims)).thenReturn("admin");
        when(jwtPayloadUtil.getRolesAsString(claims)).thenReturn("ROLE_ADMIN");
        when(jwtPayloadUtil.getPermissionsAsString(claims)).thenReturn("ALL");

        when(filterChain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/me")
                .cookie(new org.springframework.http.HttpCookie("admin_access_token", "admin-token-abc"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("JwtAuthenticationFilter should resolve admin_access_token cookie on media endpoints /api/media/**")
    @SuppressWarnings("unchecked")
    void jwtFilter_AdminCookieOnMediaEndpoint_ShouldResolveAndVerify() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(authWebClient, jwtPayloadUtil, objectMapper);

        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(authWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/auth/verify")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer admin-media-token"))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Boolean.class)).thenReturn(Mono.just(true));

        Map<String, Object> claims = Map.of("sub", "admin-456");
        when(jwtPayloadUtil.extractPayload("admin-media-token")).thenReturn(claims);
        when(jwtPayloadUtil.getUserId(claims)).thenReturn("admin-456");
        when(jwtPayloadUtil.getUsername(claims)).thenReturn("admin_media");
        when(jwtPayloadUtil.getRolesAsString(claims)).thenReturn("ROLE_ADMIN");
        when(jwtPayloadUtil.getPermissionsAsString(claims)).thenReturn("ALL");

        when(filterChain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/media/me")
                .cookie(new org.springframework.http.HttpCookie("admin_access_token", "admin-media-token"))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, filterChain).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
