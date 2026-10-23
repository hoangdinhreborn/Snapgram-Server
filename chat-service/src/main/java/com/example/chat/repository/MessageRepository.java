package com.example.chat.repository;

import com.example.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId);
}
