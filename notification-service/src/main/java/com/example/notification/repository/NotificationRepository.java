package com.example.notification.repository;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID userId, boolean isRead, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type, Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND (n.actorId IS NULL OR n.actorId NOT IN :blockedIds)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByUserIdExcludingActors(
            @Param("userId") UUID userId,
            @Param("blockedIds") List<UUID> blockedIds,
            Pageable pageable);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId
              AND n.isRead = false
              AND (n.actorId IS NULL OR n.actorId NOT IN :blockedIds)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByUserIdAndIsReadFalseExcludingActors(
            @Param("userId") UUID userId,
            @Param("blockedIds") List<UUID> blockedIds,
            Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.userId = :userId
              AND n.isRead = false
              AND (n.actorId IS NULL OR n.actorId NOT IN :blockedIds)
            """)
    long countUnreadExcludingActors(
            @Param("userId") UUID userId,
            @Param("blockedIds") List<UUID> blockedIds);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM Notification n
            WHERE (n.userId = :userA AND n.actorId = :userB)
               OR (n.userId = :userB AND n.actorId = :userA)
            """)
    void deleteMutuallyBlockedNotifications(@Param("userA") UUID userA, @Param("userB") UUID userB);

        @Query("""
            SELECT notification
            FROM Notification notification
            WHERE notification.userId = :userId
              AND notification.type = :type
              AND notification.targetType = :targetType
              AND notification.targetId = :targetId
              AND notification.isRead = false
              AND notification.createdAt > :after
            ORDER BY notification.createdAt DESC
            """)
            List<Notification> findRecentUnreadGroup(
            @Param("userId") UUID userId,
            @Param("type") NotificationType type,
            @Param("targetType") String targetType,
            @Param("targetId") String targetId,
                @Param("after") Instant after,
                Pageable pageable);

    long countByUserIdAndIsRead(UUID userId, boolean isRead);
}
