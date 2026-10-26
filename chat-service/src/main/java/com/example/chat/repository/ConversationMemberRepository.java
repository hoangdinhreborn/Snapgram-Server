package com.example.chat.repository;

import com.example.chat.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(UUID conversationId);

    Optional<ConversationMember> findByConversationIdAndUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(UUID conversationId, UUID userId);
}
