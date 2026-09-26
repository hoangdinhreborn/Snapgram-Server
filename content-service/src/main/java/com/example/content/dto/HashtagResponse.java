package com.example.content.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class HashtagResponse {
    private String id;
    private String tag;
    private long postCount;
    private Instant lastUsedAt;
}
