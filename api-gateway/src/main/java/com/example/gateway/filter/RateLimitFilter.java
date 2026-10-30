package com.example.gateway.filter;

import com.example.gateway.dto.common.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * GlobalFilter: Rate Limiting per IP.
 * Order = 0 → chạy TRƯỚC JwtAuthenticationFilter (Order = 1).
 * Redis key prefix: "gateway:rate-limit:{ip}"
 */
@Component
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final int ORDER = 0;
    private static final String KEY_PREFIX = "gateway:rate-limit:";

    private final ProxyManager<byte[]> proxyManager;
    private final boolean enabled;
    private final int capacity;
    private final long refillDurationSeconds;
    private final ObjectMapper objectMapper;

    @Autowired
    public RateLimitFilter(
            ProxyManager<byte[]> proxyManager,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.capacity:5000}") int capacity,
            @Value("${app.rate-limit.refill-duration-seconds:60}") long refillDurationSeconds,
            ObjectMapper objectMapper
    ) {
        this.proxyManager = proxyManager;
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillDurationSeconds = refillDurationSeconds;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public RateLimitFilter(
            ProxyManager<byte[]> proxyManager,
            int capacity,
            long refillDurationSeconds,
            ObjectMapper objectMapper
    ) {
        this(proxyManager, true, capacity, refillDurationSeconds, objectMapper);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Skip rate limiting if disabled
        if (!enabled) {
            return chain.filter(exchange);
        }

        // Skip CORS preflight OPTIONS requests
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        // Skip actuator health checks
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        byte[] key = (KEY_PREFIX + clientIp).getBytes(StandardCharsets.UTF_8);

        Bucket bucket = proxyManager.builder().build(key, bucketConfigSupplier());

        if (bucket.tryConsume(1)) {
            exchange.getResponse().getHeaders()
                    .add("X-Rate-Limit-Limit", String.valueOf(capacity));
            exchange.getResponse().getHeaders()
                    .add("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            exchange.getResponse().getHeaders()
                    .add("X-Rate-Limit-Reset",
                            String.valueOf(System.currentTimeMillis() / 1000 + refillDurationSeconds));
            return chain.filter(exchange);
        } else {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            return tooManyRequests(exchange);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Supplier<BucketConfiguration> bucketConfigSupplier() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(
                        capacity,
                        Refill.greedy(capacity, Duration.ofSeconds(refillDurationSeconds))
                ))
                .build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders()
                .add("X-Rate-Limit-Limit", String.valueOf(capacity));
        exchange.getResponse().getHeaders()
                .add("X-Rate-Limit-Remaining", "0");
        exchange.getResponse().getHeaders()
                .add("X-Rate-Limit-Reset",
                        String.valueOf(System.currentTimeMillis() / 1000 + refillDurationSeconds));
        exchange.getResponse().getHeaders()
                .set(HttpHeaders.RETRY_AFTER, String.valueOf(refillDurationSeconds));

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "RATE_LIMIT_EXCEEDED",
                "Too Many Requests",
                "Rate limit exceeded. Please try again later."
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorResponse);
        } catch (Exception e) {
            bytes = "{\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\",\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
