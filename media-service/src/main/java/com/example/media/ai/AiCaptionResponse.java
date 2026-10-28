package com.example.media.ai;

import java.util.List;

public record AiCaptionResponse(
        List<String> captions,
        List<String> hashtags,
        String altText,
        boolean cacheHit
) {}
