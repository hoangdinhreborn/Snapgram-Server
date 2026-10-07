package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderBlockView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommenderBlockViewRepository extends JpaRepository<RecommenderBlockView, RecommenderBlockView.RecommenderBlockId> {

    /**
     * Lấy toàn bộ danh sách User ID bị loại trừ bởi chặn hai chiều:
     * 1) Người mà userId chủ động chặn (blocker_id = userId)
     * 2) Người chặn userId (blocked_id = userId)
     */
    @Query("""
            SELECT CASE WHEN b.id.blockerId = :userId THEN b.id.blockedId ELSE b.id.blockerId END
            FROM RecommenderBlockView b
            WHERE b.id.blockerId = :userId OR b.id.blockedId = :userId
            """)
    List<UUID> findAllBlockedAndBlockerUserIds(@Param("userId") UUID userId);

    void deleteByIdBlockerIdAndIdBlockedId(UUID blockerId, UUID blockedId);
}
