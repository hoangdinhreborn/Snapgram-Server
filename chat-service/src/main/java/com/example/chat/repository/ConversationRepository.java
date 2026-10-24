package com.example.chat.repository;

import com.example.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationMember m ON m.conversationId = c.id
        WHERE m.userId = :userId AND m.leftAt IS NULL
        ORDER BY COALESCE(c.updatedAt, c.createdAt) DESC
    """)
    List<Conversation> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationMember m1 ON m1.conversationId = c.id
        JOIN ConversationMember m2 ON m2.conversationId = c.id
        WHERE c.type = 'DIRECT'
          AND m1.userId = :user1 AND m1.leftAt IS NULL
          AND m2.userId = :user2 AND m2.leftAt IS NULL
    """)
    List<Conversation> findDirectConversation(@Param("user1") UUID user1, @Param("user2") UUID user2);
}
