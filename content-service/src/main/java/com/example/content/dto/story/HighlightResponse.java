package com.example.content.dto.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightResponse {
    private String id;
    private String userId;
    private String name;
    private String coverStoryId;
    private String coverMediaUrl;
    private int storyCount;
    private Instant createdAt;
}
