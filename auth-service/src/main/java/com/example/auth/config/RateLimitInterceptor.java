package com.example.auth.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
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

    private static final int LIMIT = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "auth-service:rate-limit:";

    private final ProxyManager<byte[]> proxyManager;

    public RateLimitInterceptor(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {

        byte[] key = getClientKey(request).getBytes(StandardCharsets.UTF_8);
        Bucket bucket = proxyManager.builder().build(key, bucketConfigSupplier());

        if (bucket.tryConsume(1)) {
            addRateLimitHeaders(response, bucket);
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Please try again later.\"}");
            return false;
        }
    }

    private Supplier<BucketConfiguration> bucketConfigSupplier() {
        return () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(LIMIT, Refill.intervally(LIMIT, WINDOW)))
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
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(LIMIT));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + WINDOW.getSeconds()));
    }
}