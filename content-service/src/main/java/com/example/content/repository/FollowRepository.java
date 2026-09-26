package com.example.content.repository;

import com.example.content.entity.Follow;
import com.example.content.entity.FollowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    boolean existsByFollowerIdAndFollowingIdAndStatus(UUID followerId, UUID followingId, FollowStatus status);

    Page<Follow> findByFollowingIdAndStatus(UUID followingId, FollowStatus status, Pageable pageable);

    Page<Follow> findByFollowerIdAndStatus(UUID followerId, FollowStatus status, Pageable pageable);

    /** IDs of users that followerId is following with ACCEPTED status */
    @Query("SELECT f.followingId FROM Follow f WHERE f.followerId = :followerId AND f.status = 'ACCEPTED'")
    List<UUID> findAcceptedFollowingIds(@Param("followerId") UUID followerId);

    /** Pending follow requests TO a specific user (ordered newest first) */
    Page<Follow> findByFollowingIdAndStatusOrderByCreatedAtDesc(UUID followingId, FollowStatus status, Pageable pageable);
}
