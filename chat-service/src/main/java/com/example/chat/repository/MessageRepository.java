package com.example.chat.repository;

import com.example.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId);

    Optional<Message> findByIdAndConversationId(UUID id, UUID conversationId);

    /** Trang tin cũ hơn mốc `before` (cursor pagination). */
    Page<Message> findByConversationIdAndIsDeletedFalseAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID conversationId, Instant before, Pageable pageable);

    /**
     * Số tin chưa đọc: tin của người khác, chưa xoá.
     * Tách 2 method thay vì dùng `:after IS NULL` — PostgreSQL không suy luận được
     * kiểu của tham số NULL ("could not determine data type of parameter").
     */
    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.isDeleted = false
              AND m.senderId <> :userId
            """)
    long countUnreadAll(@Param("conversationId") UUID conversationId,
                        @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(m) FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.isDeleted = false
              AND m.senderId <> :userId
              AND m.createdAt > :after
            """)
    long countUnreadAfter(@Param("conversationId") UUID conversationId,
                          @Param("userId") UUID userId,
                          @Param("after") Instant after);
}
