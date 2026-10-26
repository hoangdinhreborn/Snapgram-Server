package com.example.auth.repository;

import com.example.auth.entity.AuthBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuthBlockRepository extends JpaRepository<AuthBlock, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    Page<AuthBlock> findAllByBlockerIdOrderByCreatedAtDesc(UUID blockerId, Pageable pageable);

    /**
     * Check mutual block: A blocked B OR B blocked A.
     */
    boolean existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(
            UUID blockerId1, UUID blockedId1,
            UUID blockerId2, UUID blockedId2
    );

    /**
     * Get all user IDs involved in a mutual block with userId:
     * 1) Users blocked by userId
     * 2) Users who blocked userId
     */
    @Query("""
            SELECT CASE WHEN b.blockerId = :userId THEN b.blockedId ELSE b.blockerId END
            FROM AuthBlock b
            WHERE b.blockerId = :userId OR b.blockedId = :userId
            """)
    List<UUID> findMutualBlockedUserIds(@Param("userId") UUID userId);
}
