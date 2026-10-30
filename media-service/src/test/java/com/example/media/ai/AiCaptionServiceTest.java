package com.example.media.ai;

import com.example.media.util.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCaptionServiceTest {

    @Mock
    private ImagePreprocessor imagePreprocessor;

    @Mock
    private AiCaptionCache captionCache;

    @Mock
    private AiCaptionRateLimiter rateLimiter;

    @Mock
    private OllamaVisionClient ollamaClient;

    @InjectMocks
    private AiCaptionService aiCaptionService;

    @Test
    @DisplayName("Cache hit should return cached suggestions immediately without consuming rate limit")
    void generateSuggestions_CacheHit_ShouldReturnCached() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        ImagePreprocessor.ProcessedVisual visual = new ImagePreprocessor.ProcessedVisual(
                new byte[]{1, 2, 3}, "base64", "hash123", 800, 600
        );
        when(imagePreprocessor.process(any(), any(), any())).thenReturn(visual);

        CaptionSuggestion cached = new CaptionSuggestion(
                List.of("Cached 1", "Cached 2", "Cached 3"),
                List.of("#cached1", "#cached2"),
                "Cached alt text"
        );
        when(captionCache.get("hash123", CaptionTone.FRIENDLY, MediaVisualType.IMAGE))
                .thenReturn(cached);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(userId);

            AiCaptionResponse response = aiCaptionService.generateSuggestions(
                    file, CaptionTone.FRIENDLY, MediaVisualType.IMAGE
            );

            assertThat(response).isNotNull();
            assertThat(response.cacheHit()).isTrue();
            assertThat(response.captions()).containsExactly("Cached 1", "Cached 2", "Cached 3");
            assertThat(response.hashtags()).containsExactly("#cached1", "#cached2");

            verifyNoInteractions(rateLimiter);
            verifyNoInteractions(ollamaClient);
        }
    }

    @Test
    @DisplayName("Cache miss should check rate limit, call Ollama, store in cache and return")
    void generateSuggestions_CacheMiss_ShouldCallOllamaAndCache() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sunset.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );

        ImagePreprocessor.ProcessedVisual visual = new ImagePreprocessor.ProcessedVisual(
                new byte[]{1, 2, 3, 4}, "base64_encoded", "hash456", 1024, 768
        );
        when(imagePreprocessor.process(any(), any(), any())).thenReturn(visual);
        when(captionCache.get("hash456", CaptionTone.FUNNY, MediaVisualType.VIDEO)).thenReturn(null);

        CaptionSuggestion fresh = new CaptionSuggestion(
                List.of("Fresh 1", "Fresh 2", "Fresh 3"),
                List.of("#tag1", "#tag2"),
                "Fresh alt text"
        );
        when(ollamaClient.generate("base64_encoded", CaptionTone.FUNNY, MediaVisualType.VIDEO))
                .thenReturn(fresh);

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getCurrentUserId).thenReturn(userId);

            AiCaptionResponse response = aiCaptionService.generateSuggestions(
                    file, CaptionTone.FUNNY, MediaVisualType.VIDEO
            );

            assertThat(response).isNotNull();
            assertThat(response.cacheHit()).isFalse();
            assertThat(response.captions()).containsExactly("Fresh 1", "Fresh 2", "Fresh 3");

            verify(rateLimiter).checkAndConsume(userId);
            verify(captionCache).put(eq("hash456"), eq(CaptionTone.FUNNY), eq(MediaVisualType.VIDEO), eq(fresh));
        }
    }
}
