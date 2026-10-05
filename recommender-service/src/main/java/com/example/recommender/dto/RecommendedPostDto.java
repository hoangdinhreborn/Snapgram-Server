package com.example.recommender.dto;

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
public class RecommendedPostDto {
    private UUID postId;
    private UUID authorId;
    private String contentType;
    private String caption;
    private String tags;
    private String visibility;
    private Instant createdAt;
    private double score;
    private String recommendationReason;
}
