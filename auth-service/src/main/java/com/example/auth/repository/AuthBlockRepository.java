package com.example.auth.repository;

import com.example.auth.entity.AuthBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
