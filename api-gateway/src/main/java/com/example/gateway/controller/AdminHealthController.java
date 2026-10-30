package com.example.gateway.controller;

import com.example.gateway.util.JwtPayloadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Controller kiểm tra sức khỏe của toàn bộ 8 microservices nội bộ.
 * Chỉ cho phép người dùng có role ADMIN truy cập.
 * Phục vụ cho Admin Portal theo kiến trúc Gateway-First (Mô hình B).
 */
@RestController
@RequestMapping("/api/admin/health")
@Slf4j
public class AdminHealthController {

    private enum AuthResult {
        OK, UNAUTHORIZED, FORBIDDEN
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient authWebClient;
    private final JwtPayloadUtil jwtPayloadUtil;
    private final WebClient webClient;
    private final Map<String, String> serviceUrls = new LinkedHashMap<>();

    public AdminHealthController(
            WebClient authWebClient,
            JwtPayloadUtil jwtPayloadUtil,
            @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl,
            @Value("${CHAT_SERVICE_URL:http://localhost:8083}") String chatUrl,
            @Value("${CONTENT_SERVICE_URL:http://localhost:8084}") String contentUrl,
            @Value("${MEDIA_SERVICE_URL:http://localhost:8085}") String mediaUrl,
            @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8086}") String notificationUrl,
            @Value("${PAYMENT_SERVICE_URL:http://localhost:8087}") String paymentUrl,
            @Value("${RECOMMENDER_SERVICE_URL:http://localhost:8090}") String recommenderUrl
    ) {
        this.authWebClient = authWebClient;
        this.jwtPayloadUtil = jwtPayloadUtil;
        this.webClient = WebClient.builder().build();
        serviceUrls.put("auth", authUrl);
        serviceUrls.put("chat", chatUrl);
        serviceUrls.put("content", contentUrl);
        serviceUrls.put("media", mediaUrl);
        serviceUrls.put("notification", notificationUrl);
        serviceUrls.put("payment", paymentUrl);
        serviceUrls.put("recommender", recommenderUrl);
    }

    @GetMapping(value = "/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> checkService(
            @PathVariable String service,
            ServerWebExchange exchange
    ) {
        return authenticateAdmin(exchange).flatMap(authResult -> {
            if (authResult == AuthResult.UNAUTHORIZED) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "DOWN", "error", "Unauthorized: Admin login required")));
            }
            if (authResult == AuthResult.FORBIDDEN) {
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("status", "DOWN", "error", "Forbidden: Admin role required")));
            }

            String id = service.toLowerCase().trim();
            if ("gateway".equals(id)) {
                return Mono.just(ResponseEntity.ok(Map.of(
                        "status", "UP",
                        "service", "gateway",
                        "groups", List.of("liveness", "readiness")
                )));
            }

            String url = serviceUrls.get(id);
            if (url == null) {
                return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "UNKNOWN", "error", "Unknown service: " + id)));
            }

            return webClient.get()
                    .uri(url + "/actuator/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .map(body -> {
                        Map<String, Object> resp = new HashMap<>();
                        resp.put("status", body.getOrDefault("status", "UP"));
                        resp.put("service", id);
                        if (body.containsKey("groups")) {
                            resp.put("groups", body.get("groups"));
                        }
                        return ResponseEntity.ok(resp);
                    })
                    .onErrorResume(ex -> {
                        log.error("Health check failed for service {} at {}: {}", id, url, ex.getMessage());
                        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Map.of(
                                        "status", "DOWN",
                                        "service", id,
                                        "error", ex.getClass().getSimpleName()
                                )));
                    });
        });
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> checkAll(ServerWebExchange exchange) {
        return authenticateAdmin(exchange).flatMap(authResult -> {
            if (authResult == AuthResult.UNAUTHORIZED) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "DOWN", "error", "Unauthorized: Admin login required")));
            }
            if (authResult == AuthResult.FORBIDDEN) {
                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("status", "DOWN", "error", "Forbidden: Admin role required")));
            }

            List<String> allServices = new ArrayList<>(List.of("gateway"));
            allServices.addAll(serviceUrls.keySet());

            return Flux.fromIterable(allServices)
                    .flatMap(s -> checkService(s, exchange).map(ResponseEntity::getBody))
                    .collectList()
                    .map(list -> ResponseEntity.ok(Map.of("services", list)));
        });
    }

    private Mono<AuthResult> authenticateAdmin(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst("admin_access_token");
            if (cookie == null) {
                cookie = exchange.getRequest().getCookies().getFirst("access_token");
            }
            if (cookie != null) {
                token = cookie.getValue();
            }
        }

        if (token == null || token.isBlank()) {
            return Mono.just(AuthResult.UNAUTHORIZED);
        }

        final String jwtToken = token;
        return authWebClient.post()
                .uri("/api/auth/verify")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorReturn(false)
                .map(valid -> {
                    if (!Boolean.TRUE.equals(valid)) {
                        return AuthResult.UNAUTHORIZED;
                    }
                    Map<String, Object> payload = jwtPayloadUtil.extractPayload(jwtToken);
                    String roles = jwtPayloadUtil.getRolesAsString(payload);
                    String permissions = jwtPayloadUtil.getPermissionsAsString(payload);
                    return canAccessHealth(roles, permissions) ? AuthResult.OK : AuthResult.FORBIDDEN;
                });
    }

    private boolean canAccessHealth(String roles, String permissions) {
        if (roles != null && !roles.isBlank()) {
            boolean isAdmin = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .anyMatch(r -> "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r));
            if (isAdmin) return true;
        }

        if (permissions != null && !permissions.isBlank()) {
            return Arrays.stream(permissions.split(","))
                    .map(String::trim)
                    .anyMatch(p -> "SYSTEM:SETTINGS_READ".equalsIgnoreCase(p) || "AUDIT:VIEW".equalsIgnoreCase(p));
        }

        return false;
    }
}
