package com.example.chat.repository;

import com.example.chat.entity.ConversationReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationReadStatusRepository
        extends JpaRepository<ConversationReadStatus, ConversationReadStatus.ReadStatusId> {

    List<ConversationReadStatus> findByIdUserIdAndIdConversationIdIn(UUID userId, Collection<UUID> conversationIds);
}
