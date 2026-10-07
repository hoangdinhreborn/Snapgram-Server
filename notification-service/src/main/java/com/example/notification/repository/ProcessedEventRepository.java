package com.example.notification.repository;

import com.example.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO notification_processed_events (id, topic, event_id, processed_at)
            VALUES (:id, :topic, :eventId, NOW())
            ON CONFLICT (topic, event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("topic") String topic, @Param("eventId") UUID eventId);
}