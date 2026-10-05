package com.example.recommender.service;

import com.example.recommender.entity.RecommenderPostView;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class RecommendationScoringEngine {

    // Lambda for exponential decay with half-life of 24 hours: ln(2) / 24 ≈ 0.02888
    private static final double DECAY_LAMBDA = 0.02888;

    /**
     * Tính điểm đề xuất cho Personal Feed:
     * Kết hợp Recency (độ mới), Affinity (độ thân thiết với tác giả), Engagement (sức hút của bài).
     */
    public double calculatePersonalScore(
            RecommenderPostView post,
            Instant now,
            Map<UUID, Long> userAuthorAffinityMap,
            long likesCount,
            Double avgWatchTimeRatio) {

        double recency = calculateRecencyScore(post.getCreatedAt(), now);
        double affinity = calculateAffinityScore(post.getAuthorId(), userAuthorAffinityMap);
        double engagement = calculateEngagementScore(likesCount, avgWatchTimeRatio);

        // Trọng số: 40% recency, 40% affinity, 20% engagement
        return (0.40 * recency) + (0.40 * affinity) + (0.20 * engagement);
    }

    /**
     * Tính điểm đề xuất cho Explore Feed:
     * Tập trung vào Engagement (sức hút cộng đồng) và Recency (bài mới).
     */
    public double calculateExploreScore(
            RecommenderPostView post,
            Instant now,
            long likesCount,
            Double avgWatchTimeRatio) {

        double recency = calculateRecencyScore(post.getCreatedAt(), now);
        double engagement = calculateEngagementScore(likesCount, avgWatchTimeRatio);

        // Trọng số Explore: 60% engagement, 40% recency
        return (0.40 * recency) + (0.60 * engagement);
    }

    private double calculateRecencyScore(Instant createdAt, Instant now) {
        if (createdAt == null) return 0.1;
        long hours = Math.max(0, Duration.between(createdAt, now).toHours());
        // Exponential decay: e^(-lambda * t)
        return Math.exp(-DECAY_LAMBDA * hours);
    }

    private double calculateAffinityScore(UUID authorId, Map<UUID, Long> affinityMap) {
        if (affinityMap == null || !affinityMap.containsKey(authorId)) {
            return 0.0;
        }
        long count = affinityMap.get(authorId);
        // Logarithmic normalization: log(1 + count) / log(1 + 50) clamped to [0, 1]
        double score = Math.log(1 + count) / Math.log(51);
        return Math.min(1.0, score);
    }

    private double calculateEngagementScore(long likesCount, Double avgWatchTimeRatio) {
        // Likes score normalized: log(1 + likes) / log(1 + 1000)
        double likeScore = Math.log(1 + likesCount) / Math.log(1001);

        double watchScore = 0.0;
        if (avgWatchTimeRatio != null) {
            watchScore = Math.min(1.0, Math.max(0.0, avgWatchTimeRatio));
        }

        return (0.60 * likeScore) + (0.40 * watchScore);
    }
}
