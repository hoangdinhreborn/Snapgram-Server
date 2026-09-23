package com.example.gateway.filter;

import com.example.gateway.util.JwtPayloadUtil;
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
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
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
    public static final String HEADER_USER_ID    = "X-User-Id";
    public static final String HEADER_USERNAME   = "X-Username";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    // Public paths: không cần JWT
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    private final WebClient authWebClient;
    private final JwtPayloadUtil jwtPayloadUtil;

    public JwtAuthenticationFilter(WebClient authWebClient, JwtPayloadUtil jwtPayloadUtil) {
        this.authWebClient = authWebClient;
        this.jwtPayloadUtil = jwtPayloadUtil;
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

        // Lấy Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Missing or invalid Authorization header for path: {}", path);
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        // Gọi auth-service verify — reactive chain
        return authWebClient.post()
                .uri("/api/auth/verify")
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve()
                .bodyToMono(Boolean.class)
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
                    Map<String, Object> payload = jwtPayloadUtil.extractPayload(token);

                    String userId   = jwtPayloadUtil.getUserId(payload);
                    String username = jwtPayloadUtil.getUsername(payload);
                    String roles    = jwtPayloadUtil.getRolesAsString(payload);

                    log.debug("Authenticated userId={}, username={}, roles={}, path={}",
                            userId, username, roles, path);

                    // Mutate request: thêm headers, xoá Authorization gốc để downstream không bị rò token
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(HEADER_USER_ID, userId)
                            .header(HEADER_USERNAME, username)
                            .header(HEADER_USER_ROLES, roles)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        // Exact match cho auth endpoints
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // Prefix match cho actuator
        return path.startsWith("/actuator");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\": \"" + message + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\": \"Authentication service unavailable\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
