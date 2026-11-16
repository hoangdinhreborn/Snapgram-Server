package com.example.recommender.dto.recommendation;

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
public class TrendingPostDto {
    private UUID postId;
    private UUID authorId;
    private String contentType;
    private String caption;
    private String tags;
    private double score;
    private long likesCount;
    private int rank;
    private Instant calculatedAt;
}
