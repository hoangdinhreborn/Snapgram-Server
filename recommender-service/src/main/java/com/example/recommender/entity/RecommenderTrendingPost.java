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
@Table(name = "recommender_trending_post")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommenderTrendingPost {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false)
    private double score;

    @Column(name = "likes_count", nullable = false)
    private long likesCount;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
}
