package com.example.media.ai;

import com.example.media.exception.InvalidMediaException;
import com.example.media.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiCaptionService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final long MAX_VIDEO_SIZE_BYTES = 20 * 1024 * 1024; // 20MB for direct video upload

    private final ImagePreprocessor imagePreprocessor;
    private final AiCaptionCache captionCache;
    private final AiCaptionRateLimiter rateLimiter;
    private final OllamaVisionClient ollamaClient;

    public AiCaptionResponse generateSuggestions(MultipartFile file, CaptionTone tone, MediaVisualType visualType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("File tải lên không được để trống");
        }

        CaptionTone effectiveTone = (tone != null) ? tone : CaptionTone.FRIENDLY;
        MediaVisualType effectiveVisualType = (visualType != null) ? visualType : MediaVisualType.IMAGE;

        // Size check
        long maxSize = (effectiveVisualType == MediaVisualType.VIDEO) ? MAX_VIDEO_SIZE_BYTES : MAX_IMAGE_SIZE_BYTES;
        if (file.getSize() > maxSize) {
            String limitDesc = (effectiveVisualType == MediaVisualType.VIDEO) ? "20MB" : "5MB";
            throw new InvalidMediaException(String.format("Dung lượng tệp vượt quá giới hạn tối đa cho phép (%s)", limitDesc));
        }

        UUID userId = UserContext.getCurrentUserId();
        log.info("Generating AI caption suggestions: user={}, tone={}, visualType={}, filename={}",
                userId, effectiveTone, effectiveVisualType, file.getOriginalFilename());

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read file bytes: {}", e.getMessage(), e);
            throw new InvalidMediaException("Không thể đọc tệp tin được tải lên");
        }

        // 1. Preprocess & extract image/keyframe, compute SHA-256
        ImagePreprocessor.ProcessedVisual processed = imagePreprocessor.process(
                rawBytes,
                file.getOriginalFilename(),
                effectiveVisualType
        );

        // 2. Tra cứu Redis Cache
        CaptionSuggestion cachedSuggestion = captionCache.get(
                processed.sha256Hex(),
                effectiveTone,
                effectiveVisualType
        );

        if (cachedSuggestion != null) {
            log.info("AI Caption Cache HIT for user={}, hash={}", userId, processed.sha256Hex());
            return new AiCaptionResponse(
                    cachedSuggestion.captions(),
                    cachedSuggestion.hashtags(),
                    cachedSuggestion.altText(),
                    true
            );
        }

        // 3. Kiểm tra và tiêu hao Rate Limit
        rateLimiter.checkAndConsume(userId);

        // 4. Gọi Ollama Vision Client
        CaptionSuggestion suggestion = ollamaClient.generate(
                processed.base64(),
                effectiveTone,
                effectiveVisualType
        );

        // 5. Lưu kết quả vào Redis Cache
        captionCache.put(
                processed.sha256Hex(),
                effectiveTone,
                effectiveVisualType,
                suggestion
        );

        log.info("Successfully generated AI caption suggestions for user={}, hash={}", userId, processed.sha256Hex());
        return new AiCaptionResponse(
                suggestion.captions(),
                suggestion.hashtags(),
                suggestion.altText(),
                false
        );
    }
}
