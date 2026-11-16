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
public class UserRecommendationDto {
    private UUID userId;
    private UUID recommendedUserId;
    private double score;
    private long mutualFriendsCount;
    private String reason;
    private Instant calculatedAt;
}
