package com.example.media.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCaptionRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AiCaptionRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new AiCaptionRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("Within limit (1st call of the minute): should consume and set TTL")
    void checkAndConsume_WithinLimit_ShouldSucceed() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment("ai-caption:rl:min:" + userId)).thenReturn(1L);
        when(valueOperations.increment("ai-caption:rl:day:" + userId)).thenReturn(1L);

        rateLimiter.checkAndConsume(userId);

        verify(redisTemplate).expire(eq("ai-caption:rl:min:" + userId), any(java.time.Duration.class));
        verify(redisTemplate).expire(eq("ai-caption:rl:day:" + userId), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("Exceed minute rate limit (6th call): should throw RateLimitExceededException with retry-after")
    void checkAndConsume_ExceedMinuteLimit_ShouldThrowRateLimitExceeded() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment("ai-caption:rl:min:" + userId)).thenReturn(6L);
        when(redisTemplate.getExpire("ai-caption:rl:min:" + userId)).thenReturn(45L);

        assertThatThrownBy(() -> rateLimiter.checkAndConsume(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(45L);
                    assertThat(rle.getMessage()).contains("5 lần/phút");
                });
    }

    @Test
    @DisplayName("Exceed daily rate limit (51st call): should throw RateLimitExceededException")
    void checkAndConsume_ExceedDailyLimit_ShouldThrowRateLimitExceeded() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment("ai-caption:rl:min:" + userId)).thenReturn(2L);
        when(valueOperations.increment("ai-caption:rl:day:" + userId)).thenReturn(51L);
        when(redisTemplate.getExpire("ai-caption:rl:day:" + userId)).thenReturn(3600L);

        assertThatThrownBy(() -> rateLimiter.checkAndConsume(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(3600L);
                    assertThat(rle.getMessage()).contains("50 lần");
                });
    }

    @Test
    @DisplayName("Redis exception should fail-open and not block user")
    void checkAndConsume_RedisThrowsException_ShouldFailOpen() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        // Should not throw any exception
        rateLimiter.checkAndConsume(userId);
    }
}
