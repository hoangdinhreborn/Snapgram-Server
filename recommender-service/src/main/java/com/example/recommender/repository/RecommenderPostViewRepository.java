package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderPostView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecommenderPostViewRepository extends JpaRepository<RecommenderPostView, UUID> {

    /**
     * Lấy bài viết từ danh sách tác giả đang theo dõi (và bản thân), loại trừ người bị block/mute
     */
    @Query("""
            SELECT p FROM RecommenderPostView p
            WHERE p.authorId IN :authorIds
              AND (:excludedAuthorIds IS NULL OR p.authorId NOT IN :excludedAuthorIds)
            ORDER BY p.createdAt DESC
            """)
    List<RecommenderPostView> findFeedCandidates(
            @Param("authorIds") Collection<UUID> authorIds,
            @Param("excludedAuthorIds") Collection<UUID> excludedAuthorIds,
            Pageable pageable);

    /**
     * Lấy các bài viết khám phá (Explore):
     * 1) Public visibility
     * 2) Tác giả không private
     * 3) Không phải bài của chính mình hoặc người đang follow
     * 4) Không thuộc danh sách block/mute
     * 5) Trong khoảng thời gian gần đây (sau :since)
     */
    @Query("""
            SELECT p FROM RecommenderPostView p
            WHERE p.visibility = 'PUBLIC'
              AND p.authorIsPrivate = false
              AND p.authorId NOT IN :followingAndSelfIds
              AND (:excludedAuthorIds IS NULL OR p.authorId NOT IN :excludedAuthorIds)
              AND p.createdAt >= :since
            ORDER BY p.createdAt DESC
            """)
    List<RecommenderPostView> findExploreCandidates(
            @Param("followingAndSelfIds") Collection<UUID> followingAndSelfIds,
            @Param("excludedAuthorIds") Collection<UUID> excludedAuthorIds,
            @Param("since") Instant since,
            Pageable pageable);

    /**
     * Fallback Explore khi chưa có tương tác hoặc hệ thống mới: lấy bài Public mới nhất
     */
    @Query("""
            SELECT p FROM RecommenderPostView p
            WHERE p.visibility = 'PUBLIC'
              AND p.authorIsPrivate = false
              AND (:excludedAuthorIds IS NULL OR p.authorId NOT IN :excludedAuthorIds)
            ORDER BY p.createdAt DESC
            """)
    List<RecommenderPostView> findLatestPublicPosts(
            @Param("excludedAuthorIds") Collection<UUID> excludedAuthorIds,
            Pageable pageable);

    @Modifying
    @Query("UPDATE RecommenderPostView p SET p.authorIsPrivate = :isPrivate WHERE p.authorId = :authorId")
    void updateAuthorPrivacy(@Param("authorId") UUID authorId, @Param("isPrivate") boolean isPrivate);

    @Query("""
            SELECT p.tags FROM RecommenderPostView p
            WHERE p.tags IS NOT NULL AND p.tags <> '' AND p.createdAt >= :since
            """)
    List<String> findRecentTags(@Param("since") Instant since);
}
