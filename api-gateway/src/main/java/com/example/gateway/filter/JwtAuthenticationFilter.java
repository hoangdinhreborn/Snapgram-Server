package com.example.gateway.filter;

import com.example.gateway.dto.ErrorResponse;
import com.example.gateway.util.JwtPayloadUtil;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GlobalFilter: JWT Authentication + Header Injection.
 *
 * <p>Flow:
 * 1. Skip public paths (login, register, refresh, actuator)
 * 2. Extract Bearer token từ Authorization header
 * 3. Gọi auth-service POST /api/auth/verify để validate token
 *    (auth-service là source of truth — check signature + blacklist)
 * 4. Decode JWT payload (Base64, không verify lại signature)
 * 5. Inject downstream headers: X-User-Id, X-Username, X-User-Roles
 *
 * <p>Order = 1 → chạy SAU RateLimitFilter (Order = 0).
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 1;

    // Headers inject xuống downstream services
    public static final String HEADER_USER_ID          = "X-User-Id";
    public static final String HEADER_USERNAME         = "X-Username";
    public static final String HEADER_USER_ROLES       = "X-User-Roles";
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    // Public paths: không cần JWT
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/admin/login",
            "/api/auth/login/2fa",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/verify-email",
            "/api/users/batch"
    );

    private final WebClient authWebClient;
    private final JwtPayloadUtil jwtPayloadUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(WebClient authWebClient, JwtPayloadUtil jwtPayloadUtil, ObjectMapper objectMapper) {
        this.authWebClient = authWebClient;
        this.jwtPayloadUtil = jwtPayloadUtil;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // WebSocket handshakes from browsers cannot carry Authorization headers.
        // The notification handler resolves the user from the X-User-Id / ?userId= handshake data.
        if (path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        // Lấy token từ Authorization header hoặc Cookie
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            org.springframework.http.HttpCookie cookie = null;
            if (path.startsWith("/api/auth/admin") || path.startsWith("/api/admin")) {
                cookie = findFirstNonBlankCookie(exchange, "admin_access_token");
                if (cookie == null) {
                    cookie = findFirstNonBlankCookie(exchange, "access_token");
                }
            } else {
                cookie = findFirstNonBlankCookie(exchange, "access_token");
                if (cookie == null) {
                    cookie = findFirstNonBlankCookie(exchange, "admin_access_token");
                }
            }
            if (cookie != null) {
                token = cookie.getValue();
            }
        }

        if (token == null || token.isBlank()) {
            if (isOptionalAuthPath(exchange.getRequest().getMethod().name(), path)) {
                return chain.filter(exchange);
            }
            log.debug("Missing or invalid token for path: {}", path);
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        final String jwtToken = token;
        String bearerHeader = "Bearer " + jwtToken;

        // Gọi auth-service verify — reactive chain
        return authWebClient.post()
                .uri("/api/auth/verify")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode().is4xxClientError()) {
                        log.debug("Auth-service rejected token with status {}: {}", ex.getStatusCode(), ex.getMessage());
                        return Mono.just(false);
                    }
                    log.error("Auth-service returned error status {}: {}", ex.getStatusCode(), ex.getMessage());
                    return Mono.empty();
                })
                .onErrorResume(ex -> {
                    log.error("Auth service unavailable: {}", ex.getMessage());
                    return Mono.empty(); // empty → xử lý ở switchIfEmpty
                })
                .switchIfEmpty(Mono.defer(() -> serviceUnavailable(exchange).then(Mono.empty())))
                .flatMap(valid -> {
                    if (!Boolean.TRUE.equals(valid)) {
                        log.debug("Token rejected by auth-service for path: {}", path);
                        return unauthorized(exchange, "Invalid or expired token");
                    }

                    // Token hợp lệ → decode payload để inject headers
                    Map<String, Object> payload = jwtPayloadUtil.extractPayload(jwtToken);

                    String userId      = jwtPayloadUtil.getUserId(payload);
                    String username    = jwtPayloadUtil.getUsername(payload);
                    String roles       = jwtPayloadUtil.getRolesAsString(payload);
                    String permissions = jwtPayloadUtil.getPermissionsAsString(payload);

                    log.debug("Authenticated userId={}, username={}, roles={}, permissions={}, path={}",
                            userId, username, roles, permissions, path);

                    // Mutate request: ghi đè headers đã xác thực, đảm bảo downstream nhận thông tin tin cậy tuyệt đối
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(httpHeaders -> {
                                httpHeaders.set(HEADER_USER_ID, userId);
                                httpHeaders.set(HEADER_USERNAME, username);
                                httpHeaders.set(HEADER_USER_ROLES, roles);
                                httpHeaders.set(HEADER_USER_PERMISSIONS, permissions);
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private org.springframework.http.HttpCookie findFirstNonBlankCookie(ServerWebExchange exchange, String name) {
        List<org.springframework.http.HttpCookie> cookies = exchange.getRequest().getCookies().get(name);
        if (cookies == null || cookies.isEmpty()) {
            return null;
        }
        for (org.springframework.http.HttpCookie c : cookies) {
            if (c.getValue() != null && !c.getValue().isBlank()) {
                return c;
            }
        }
        return null;
    }

    private boolean isPublicPath(String path) {
        // Exact match cho auth endpoints
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // Prefix match cho actuator
        return path.startsWith("/actuator");
    }

    private boolean isOptionalAuthPath(String method, String path) {
        if ("GET".equalsIgnoreCase(method)) {
            if (path.startsWith("/api/posts/explore") ||
                path.startsWith("/api/posts/user/") ||
                (path.startsWith("/api/posts/") && !path.equals("/api/posts/me") && !path.equals("/api/posts/feed")) ||
                path.startsWith("/api/users/search") ||
                path.startsWith("/api/hashtags") ||
                (path.startsWith("/api/users/") && !path.startsWith("/api/users/me"))) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                "Unauthorized",
                message
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (Exception e) {
            bytes = ("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                "Service Unavailable",
                "Authentication service unavailable"
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (Exception e) {
            bytes = "{\"status\":503,\"code\":\"SERVICE_UNAVAILABLE\",\"error\":\"Service Unavailable\",\"message\":\"Authentication service unavailable\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
