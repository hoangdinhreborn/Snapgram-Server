package com.example.media.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCaptionCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiCaptionCache captionCache;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        captionCache = new AiCaptionCache(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("Cache miss: should return null")
    void get_CacheMiss_ShouldReturnNull() {
        String sha256 = "dummy_sha256_hash";
        when(valueOperations.get("ai-caption:cache:" + sha256 + ":FRIENDLY:IMAGE")).thenReturn(null);

        CaptionSuggestion result = captionCache.get(sha256, CaptionTone.FRIENDLY, MediaVisualType.IMAGE);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Cache hit: should deserialize CaptionSuggestion")
    void get_CacheHit_ShouldReturnCaptionSuggestion() {
        String sha256 = "dummy_sha256_hash";
        String json = """
                {
                  "captions": ["Caption 1", "Caption 2", "Caption 3"],
                  "hashtags": ["#snapgram", "#vibes"],
                  "altText": "Sample alt text."
                }
                """;
        when(valueOperations.get("ai-caption:cache:" + sha256 + ":FUNNY:VIDEO")).thenReturn(json);

        CaptionSuggestion result = captionCache.get(sha256, CaptionTone.FUNNY, MediaVisualType.VIDEO);

        assertThat(result).isNotNull();
        assertThat(result.captions()).containsExactly("Caption 1", "Caption 2", "Caption 3");
        assertThat(result.hashtags()).containsExactly("#snapgram", "#vibes");
        assertThat(result.altText()).isEqualTo("Sample alt text.");
    }

    @Test
    @DisplayName("Put suggestion to cache: should serialize and store with 24h TTL")
    void put_ShouldSerializeAndSetWithTtl() {
        String sha256 = "dummy_sha256_hash";
        CaptionSuggestion suggestion = new CaptionSuggestion(
                List.of("Caption A"),
                List.of("#tagA"),
                "Alt text A"
        );

        captionCache.put(sha256, CaptionTone.SHORT, MediaVisualType.IMAGE, suggestion);

        verify(valueOperations).set(
                eq("ai-caption:cache:" + sha256 + ":SHORT:IMAGE"),
                contains("Caption A"),
                eq(Duration.ofHours(24))
        );
    }
}
