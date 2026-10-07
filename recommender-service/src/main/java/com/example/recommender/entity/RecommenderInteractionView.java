package com.example.recommender.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommender_interaction_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommenderInteractionView {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "watch_time_ratio", precision = 6, scale = 5)
    private BigDecimal watchTimeRatio;

    @Column(name = "explicit_rating", precision = 4, scale = 2)
    private BigDecimal explicitRating;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
