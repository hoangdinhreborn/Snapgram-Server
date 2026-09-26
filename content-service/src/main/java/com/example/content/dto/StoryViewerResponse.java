package com.example.content.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class StoryViewerResponse {
    private String viewerId;
    private Instant viewedAt;
}
