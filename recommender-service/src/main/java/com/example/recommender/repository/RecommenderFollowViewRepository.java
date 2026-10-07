package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderFollowView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommenderFollowViewRepository extends JpaRepository<RecommenderFollowView, RecommenderFollowView.RecommenderFollowId> {

    @Query("SELECT f.id.followingId FROM RecommenderFollowView f WHERE f.id.followerId = :userId AND f.status = 'ACCEPTED'")
    List<UUID> findFollowingIds(@Param("userId") UUID userId);

    @Query("SELECT f.id.followerId FROM RecommenderFollowView f WHERE f.id.followingId = :userId AND f.status = 'ACCEPTED'")
    List<UUID> findFollowerIds(@Param("userId") UUID userId);

    /**
     * Tìm danh sách người dùng được theo dõi bởi những người mà tôi đang theo dõi (Friend-of-friends / 2nd degree follows)
     * để gợi ý tài khoản mới, loại trừ những người tôi đã follow hoặc chính tôi.
     */
    @Query("""
            SELECT f2.id.followingId, COUNT(f2.id.followerId) as mutualCount
            FROM RecommenderFollowView f1
            JOIN RecommenderFollowView f2 ON f1.id.followingId = f2.id.followerId
            WHERE f1.id.followerId = :userId
              AND f1.status = 'ACCEPTED'
              AND f2.status = 'ACCEPTED'
              AND f2.id.followingId <> :userId
              AND f2.id.followingId NOT IN (
                  SELECT f3.id.followingId FROM RecommenderFollowView f3
                  WHERE f3.id.followerId = :userId AND f3.status = 'ACCEPTED'
              )
            GROUP BY f2.id.followingId
            ORDER BY COUNT(f2.id.followerId) DESC
            """)
    List<Object[]> findFriendOfFriendsWithMutualCount(@Param("userId") UUID userId);

    void deleteByIdFollowerIdAndIdFollowingId(UUID followerId, UUID followingId);
}
