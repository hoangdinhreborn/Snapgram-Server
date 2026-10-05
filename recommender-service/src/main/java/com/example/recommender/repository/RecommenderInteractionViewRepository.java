package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderInteractionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommenderInteractionViewRepository extends JpaRepository<RecommenderInteractionView, UUID> {

    List<RecommenderInteractionView> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT COUNT(i) FROM RecommenderInteractionView i WHERE i.postId = :postId AND i.type = 'LIKE'")
    long countLikesByPostId(@Param("postId") UUID postId);

    @Query("SELECT COUNT(i) FROM RecommenderInteractionView i WHERE i.postId = :postId AND i.type = 'VIEW'")
    long countViewsByPostId(@Param("postId") UUID postId);

    @Query("SELECT AVG(i.watchTimeRatio) FROM RecommenderInteractionView i WHERE i.postId = :postId AND i.watchTimeRatio IS NOT NULL")
    Double findAvgWatchTimeRatioByPostId(@Param("postId") UUID postId);

    /**
     * Lấy danh sách authorId mà userId đã tương tác nhiều nhất kèm số lần tương tác (để tính User Affinity)
     */
    @Query("""
            SELECT p.authorId, COUNT(i.id)
            FROM RecommenderInteractionView i
            JOIN RecommenderPostView p ON i.postId = p.postId
            WHERE i.userId = :userId
            GROUP BY p.authorId
            ORDER BY COUNT(i.id) DESC
            """)
    List<Object[]> findAffinityCountsByUserId(@Param("userId") UUID userId);

    void deleteByPostId(UUID postId);
}
