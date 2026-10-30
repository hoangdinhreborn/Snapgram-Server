package com.example.media.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiCaptionCache {

    private static final String KEY_PREFIX = "ai-caption:cache:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CaptionSuggestion get(String sha256Hex, CaptionTone tone, MediaVisualType visualType) {
        String key = buildKey(sha256Hex, tone, visualType);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                log.debug("AI Caption Cache HIT for key: {}", key);
                return objectMapper.readValue(json, CaptionSuggestion.class);
            }
        } catch (Exception e) {
            log.warn("Failed to read from Redis cache for key {}: {}. Proceeding without cache.", key, e.getMessage());
        }
        return null;
    }

    public void put(String sha256Hex, CaptionTone tone, MediaVisualType visualType, CaptionSuggestion suggestion) {
        if (suggestion == null) return;
        String key = buildKey(sha256Hex, tone, visualType);
        try {
            String json = objectMapper.writeValueAsString(suggestion);
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL);
            log.debug("Saved AI Caption to cache with key: {}", key);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize CaptionSuggestion to JSON: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to write to Redis cache for key {}: {}", key, e.getMessage());
        }
    }

    private String buildKey(String sha256Hex, CaptionTone tone, MediaVisualType visualType) {
        return KEY_PREFIX + sha256Hex + ":" + tone.name() + ":" + visualType.name();
    }
}
