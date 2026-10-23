package com.example.auth.config;

import com.example.auth.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Supplier;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "auth-service:rate-limit:";

    private final ProxyManager<byte[]> proxyManager;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int limit;
    private final Duration window;

    @Autowired
    public RateLimitInterceptor(
            ProxyManager<byte[]> proxyManager,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.limit:5000}") int limit,
            @Value("${app.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this(proxyManager, new ObjectMapper(), enabled, limit, Duration.ofSeconds(windowSeconds));
    }

    public RateLimitInterceptor(ProxyManager<byte[]> proxyManager, ObjectMapper objectMapper) {
        this(proxyManager, objectMapper, true, 100, Duration.ofMinutes(1));
    }

    public RateLimitInterceptor(
            ProxyManager<byte[]> proxyManager,
            ObjectMapper objectMapper,
            boolean enabled,
            int limit,
            Duration window
    ) {
        this.proxyManager = proxyManager;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.enabled = enabled;
        this.limit = limit;
        this.window = window;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        if (!enabled) {
            return true;
        }

        // Bỏ qua request preflight CORS
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        byte[] key = getClientKey(request).getBytes(StandardCharsets.UTF_8);
        Bucket bucket = proxyManager.builder().build(key, bucketConfigSupplier());

        if (bucket.tryConsume(1)) {
            addRateLimitHeaders(response, bucket);
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(window.getSeconds()));
            response.setHeader("X-Rate-Limit-Limit", String.valueOf(limit));
            response.setHeader("X-Rate-Limit-Remaining", "0");
            response.setHeader("X-Rate-Limit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + window.getSeconds()));

            ErrorResponse errorResponse = ErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMIT_EXCEEDED",
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later."
            );
            String json;
            try {
                json = objectMapper.writeValueAsString(errorResponse);
            } catch (Exception e) {
                json = "{\"status\":429,\"code\":\"RATE_LIMIT_EXCEEDED\",\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}";
            }
            response.getWriter().write(json);
            return false;
        }
    }

    private Supplier<BucketConfiguration> bucketConfigSupplier() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(limit, Refill.greedy(limit, window)))
                .build();
    }

    private String getClientKey(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        String endpoint = request.getRequestURI();
        return KEY_PREFIX + clientIp + ":" + endpoint;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void addRateLimitHeaders(HttpServletResponse response, Bucket bucket) {
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(limit));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + window.getSeconds()));
    }
}