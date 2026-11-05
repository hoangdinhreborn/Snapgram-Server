package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderTrendingPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecommenderTrendingPostRepository extends JpaRepository<RecommenderTrendingPost, UUID> {

    @Query("""
            SELECT p FROM RecommenderTrendingPost p
            ORDER BY p.score DESC, p.postId ASC
            """)
    List<RecommenderTrendingPost> findTopTrending(Pageable pageable);

    @Query("""
            SELECT p FROM RecommenderTrendingPost p
            ORDER BY p.calculatedAt DESC, p.score DESC
            """)
    List<RecommenderTrendingPost> findLatestTrending(Pageable pageable);

    long deleteByCalculatedAtBefore(Instant cutoff);
}
