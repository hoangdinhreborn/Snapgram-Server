package com.example.media.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiCaptionRateLimiter {

    public static final int LIMIT_PER_MINUTE = 5;
    public static final int LIMIT_PER_DAY = 50;

    private static final String PREFIX_MIN = "ai-caption:rl:min:";
    private static final String PREFIX_DAY = "ai-caption:rl:day:";

    private final StringRedisTemplate redisTemplate;

    public void checkAndConsume(UUID userId) {
        if (userId == null) {
            return;
        }

        String minKey = PREFIX_MIN + userId;
        String dayKey = PREFIX_DAY + userId;

        try {
            Long minCount = redisTemplate.opsForValue().increment(minKey);
            if (minCount != null && minCount == 1L) {
                redisTemplate.expire(minKey, Duration.ofSeconds(60));
            }

            if (minCount != null && minCount > LIMIT_PER_MINUTE) {
                Long ttlSeconds = redisTemplate.getExpire(minKey);
                long retryAfter = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : 60L;
                log.warn("Rate limit per minute exceeded for user {}: count={}, retryAfter={}s", userId, minCount, retryAfter);
                throw new RateLimitExceededException("Bạn đã gọi quá giới hạn 5 lần/phút. Vui lòng thử lại sau.", retryAfter);
            }

            Long dayCount = redisTemplate.opsForValue().increment(dayKey);
            if (dayCount != null && dayCount == 1L) {
                redisTemplate.expire(dayKey, Duration.ofDays(1));
            }

            if (dayCount != null && dayCount > LIMIT_PER_DAY) {
                Long ttlSeconds = redisTemplate.getExpire(dayKey);
                long retryAfter = (ttlSeconds != null && ttlSeconds > 0) ? ttlSeconds : 3600L;
                log.warn("Rate limit per day exceeded for user {}: count={}, retryAfter={}s", userId, dayCount, retryAfter);
                throw new RateLimitExceededException("Bạn đã sử dụng hết hạn mức gợi ý AI 50 lần trong ngày hôm nay.", retryAfter);
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis rate limiter encounter error for user {}: {}. Allowing request (fail-open).", userId, e.getMessage());
        }
    }
}
