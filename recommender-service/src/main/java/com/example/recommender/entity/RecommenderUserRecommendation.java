package com.example.recommender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommender_user_recommendation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommenderUserRecommendation {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recommended_user_id", nullable = false)
    private UUID recommendedUserId;

    @Column(nullable = false)
    private double score;

    @Column(name = "mutual_friends_count", nullable = false)
    private long mutualFriendsCount;

    @Column(length = 255)
    private String reason;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
