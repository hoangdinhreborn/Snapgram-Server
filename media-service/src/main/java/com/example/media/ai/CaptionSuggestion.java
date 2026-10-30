package com.example.media.ai;

import java.util.List;

public record CaptionSuggestion(
        List<String> captions,
        List<String> hashtags,
        String altText
) {}
