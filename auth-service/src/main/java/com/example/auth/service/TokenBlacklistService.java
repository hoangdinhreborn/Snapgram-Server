package com.example.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages the access token blacklist in Redis.
 *
 * <p>When a user logs out, their access token's JTI is stored in Redis
 * with a TTL equal to the token's remaining lifetime. Any subsequent request
 * bearing that token is rejected even though the JWT signature is still valid.
 *
 * <p>Redis keys: {@code auth-service:blacklist:{jti}}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth-service:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Blacklist an access token JTI for the given TTL in seconds.
     * If ttlSeconds <= 0 the token is already expired — nothing to store.
     */
    public void blacklist(String jti, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            log.debug("Access token already expired, skipping blacklist for jti={}", jti);
            return;
        }
        stringRedisTemplate.opsForValue()
                .set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        log.debug("Access token blacklisted in Redis: jti={}, ttl={}s", jti, ttlSeconds);
    }

    /**
     * Returns true if the given JTI has been blacklisted (i.e. the user logged out
     * and the original token has not yet expired naturally).
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
