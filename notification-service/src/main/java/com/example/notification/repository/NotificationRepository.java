package com.example.notification.repository;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID userId, boolean isRead, Pageable pageable);

    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type, Pageable pageable);

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
