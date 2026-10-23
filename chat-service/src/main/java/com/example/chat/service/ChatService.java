package com.example.chat.service;

import com.example.chat.dto.ConversationResponse;
import com.example.chat.dto.MessageResponse;
import com.example.chat.dto.SendMessageRequest;
import com.example.chat.entity.Conversation;
import com.example.chat.entity.ConversationMember;
import com.example.chat.entity.Message;
import com.example.chat.repository.ConversationMemberRepository;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public ConversationResponse getOrCreateDirectConversation(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot create a direct conversation with yourself");
        }

        List<Conversation> existing = conversationRepository.findDirectConversation(currentUserId, targetUserId);
        if (!existing.isEmpty()) {
            return toConversationResponse(existing.get(0));
        }

        Conversation conv = Conversation.builder()
                .type("DIRECT")
                .createdBy(currentUserId)
                .build();
        Conversation saved = conversationRepository.save(conv);

        ConversationMember m1 = ConversationMember.builder()
                .conversationId(saved.getId())
                .userId(currentUserId)
                .role("ADMIN")
                .build();
        ConversationMember m2 = ConversationMember.builder()
                .conversationId(saved.getId())
                .userId(targetUserId)
                .role("MEMBER")
                .build();
        memberRepository.save(m1);
        memberRepository.save(m2);

        return toConversationResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        List<Conversation> convs = conversationRepository.findActiveByUserId(userId);
        return convs.stream().map(this::toConversationResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getConversationMessages(UUID conversationId, UUID userId, int page, int size) {
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new RuntimeException("Access denied: You are not a member of this conversation");
        }

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Message> messages = messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageable);
        return messages.map(this::toMessageResponse);
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, senderId)) {
            throw new RuntimeException("Access denied: You are not a member of this conversation");
        }

        Message msg = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(request.getType() != null ? request.getType() : "TEXT")
                .content(request.getContent().trim())
                .mediaUrl(request.getMediaUrl())
                .build();
        Message saved = messageRepository.save(msg);

        // Update conversation's updatedAt
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setUpdatedAt(Instant.now());
            conversationRepository.save(c);
        });

        return toMessageResponse(saved);
    }

    private ConversationResponse toConversationResponse(Conversation c) {
        List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(c.getId());
        List<String> memberIds = members.stream().map(m -> m.getUserId().toString()).collect(Collectors.toList());

        MessageResponse lastMsg = messageRepository.findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(c.getId())
                .map(this::toMessageResponse)
                .orElse(null);

        return ConversationResponse.builder()
                .id(c.getId().toString())
                .type(c.getType())
                .name(c.getName())
                .avatarUrl(c.getAvatarUrl())
                .memberIds(memberIds)
                .lastMessage(lastMsg)
                .updatedAt(c.getUpdatedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId().toString())
                .conversationId(m.getConversationId().toString())
                .senderId(m.getSenderId().toString())
                .type(m.getType())
                .content(m.getContent())
                .mediaUrl(m.getMediaUrl())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
