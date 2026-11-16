package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderUserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecommenderUserRecommendationRepository extends JpaRepository<RecommenderUserRecommendation, UUID> {

    @Query("""
            SELECT r FROM RecommenderUserRecommendation r
            WHERE r.userId = :userId
            ORDER BY r.calculatedAt DESC, r.score DESC
            """)
    List<RecommenderUserRecommendation> findTopRecommendationsForUser(@Param("userId") UUID userId, Pageable pageable);

    long deleteByCalculatedAtBefore(Instant cutoff);

    long deleteByUserId(UUID userId);
}
