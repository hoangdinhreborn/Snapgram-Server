package com.example.content.dto.story;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighlightDetailResponse {
    private String id;
    private String userId;
    private String name;
    private String coverStoryId;
    private String coverMediaUrl;
    private int storyCount;
    private Instant createdAt;
    private List<StoryResponse> stories;
}
