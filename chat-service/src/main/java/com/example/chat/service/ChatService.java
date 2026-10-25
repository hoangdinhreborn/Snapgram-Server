package com.example.chat.service;

import com.example.chat.client.AuthServiceClient;
import com.example.chat.dto.ConversationResponse;
import com.example.chat.dto.MessagePageResponse;
import com.example.chat.dto.MessageResponse;
import com.example.chat.dto.PartnerResponse;
import com.example.chat.dto.SendMessageRequest;
import com.example.chat.entity.Conversation;
import com.example.chat.entity.ConversationMember;
import com.example.chat.entity.ConversationReadStatus;
import com.example.chat.entity.Message;
import com.example.chat.exception.AccessDeniedException;
import com.example.chat.exception.NotFoundException;
import com.example.chat.messaging.ChatEventPublisher;
import com.example.chat.repository.ConversationMemberRepository;
import com.example.chat.repository.ConversationReadStatusRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final ConversationReadStatusRepository readStatusRepository;
    private final AuthServiceClient authServiceClient;
    private final ChatEventPublisher eventPublisher;
    private final ChatRealtimeService realtimeService;

    // ---- Hội thoại ----

    @Transactional
    public ConversationResponse getOrCreateDirectConversation(UUID currentUserId, UUID targetUserId) {
        if (targetUserId == null) {
            throw new IllegalArgumentException("targetUserId là bắt buộc");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tạo hội thoại với chính mình");
        }
        if (authServiceClient.isMutuallyBlocked(currentUserId, targetUserId)) {
            throw new NotFoundException("Người dùng không tồn tại");
        }

        List<Conversation> existing = conversationRepository.findDirectConversation(currentUserId, targetUserId);
        if (!existing.isEmpty()) {
            return enrich(existing.get(0), currentUserId);
        }

        Conversation conv = Conversation.builder()
                .type("DIRECT")
                .createdBy(currentUserId)
                .build();
        Conversation saved = conversationRepository.save(conv);

        memberRepository.save(ConversationMember.builder()
                .conversationId(saved.getId())
                .userId(currentUserId)
                .role("ADMIN")
                .build());
        memberRepository.save(ConversationMember.builder()
                .conversationId(saved.getId())
                .userId(targetUserId)
                .role("MEMBER")
                .build());

        return enrich(saved, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findActiveByUserId(userId);
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        Map<UUID, List<ConversationMember>> membersByConversation = new HashMap<>();
        for (UUID conversationId : conversationIds) {
            membersByConversation.put(conversationId,
                    memberRepository.findByConversationIdAndLeftAtIsNull(conversationId));
        }

        Map<UUID, UUID> myLastRead = readStatusRepository
                .findByIdUserIdAndIdConversationIdIn(userId, conversationIds)
                .stream()
                .collect(Collectors.toMap(
                        rs -> rs.getId().getConversationId(),
                        ConversationReadStatus::getLastReadMessageId,
                        (a, b) -> a
                ));

        List<UUID> partnerIds = new ArrayList<>();
        for (List<ConversationMember> members : membersByConversation.values()) {
            for (ConversationMember member : members) {
                if (!member.getUserId().equals(userId) && !partnerIds.contains(member.getUserId())) {
                    partnerIds.add(member.getUserId());
                }
            }
        }
        Map<UUID, AuthServiceClient.UserSummary> summaries = authServiceClient.getUsersSummaryMap(partnerIds);

        List<ConversationResponse> result = new ArrayList<>(conversations.size());
        for (Conversation conversation : conversations) {
            List<ConversationMember> members = membersByConversation.getOrDefault(conversation.getId(), List.of());
            ConversationMember me = members.stream()
                    .filter(m -> m.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);

            UUID lastReadMessageId = myLastRead.get(conversation.getId());
            Instant readAt = lastReadMessageId == null ? null
                    : messageRepository.findById(lastReadMessageId).map(Message::getCreatedAt).orElse(null);

            long unread = countUnread(conversation.getId(), userId, readAt);

            result.add(toResponse(
                    conversation,
                    userId,
                    members,
                    summaries,
                    unread,
                    lastReadMessageId,
                    me != null && me.isMuted()
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID userId) {
        Conversation conversation = requireConversation(conversationId);
        requireMember(conversationId, userId);
        return enrich(conversation, userId);
    }

    // ---- Tin nhắn ----

    @Transactional(readOnly = true)
    public MessagePageResponse getConversationMessages(UUID conversationId, UUID userId,
                                                       String before, int page, int size) {
        requireMember(conversationId, userId);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        Page<Message> messages;
        if (before != null && !before.isBlank()) {
            Instant cursor = messageRepository.findById(parseUuid(before))
                    .map(Message::getCreatedAt)
                    .orElse(null);
            messages = cursor == null
                    ? messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageable)
                    : messageRepository.findByConversationIdAndIsDeletedFalseAndCreatedAtBeforeOrderByCreatedAtDesc(
                            conversationId, cursor, pageable);
        } else {
            messages = messageRepository.findByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId, pageable);
        }

        // Repository trả mới → cũ; đảo lại thành cũ → mới cho UI chat.
        List<MessageResponse> content = new ArrayList<>(messages.getContent().stream()
                .map(this::toMessageResponse)
                .toList());
        Collections.reverse(content);

        String nextCursor = messages.hasNext() && !content.isEmpty()
                ? content.get(0).getId()
                : null;

        return MessagePageResponse.builder()
                .content(content)
                .hasMore(messages.hasNext())
                .nextCursor(nextCursor)
                .totalElements(messages.getTotalElements())
                .build();
    }

    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        requireMember(conversationId, senderId);

        String content = request.getContent() != null ? request.getContent().trim() : "";
        boolean hasMedia = request.getMediaUrl() != null && !request.getMediaUrl().isBlank();
        if (content.isEmpty() && !hasMedia) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hội thoại"));

        List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId);
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(senderId)) {
                if (authServiceClient.isMutuallyBlocked(senderId, member.getUserId())) {
                    throw new NotFoundException("Người dùng không tồn tại");
                }
            }
        }

        Message message = Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(request.getType() != null ? request.getType() : "TEXT")
                .content(content.isEmpty() ? null : content)
                .mediaUrl(request.getMediaUrl())
                .build();
        Message saved = messageRepository.save(message);

        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        MessageResponse response = toMessageResponse(saved);

        // Realtime cho các tab đang mở
        realtimeService.broadcastMessage(response);
        // Thông báo cho người nhận (kể cả khi họ offline)
        eventPublisher.publishMessageCreated(saved, senderId, members);

        return response;
    }

    @Transactional
    public void deleteMessage(UUID conversationId, UUID messageId, UUID requesterId) {
        requireMember(conversationId, requesterId);

        Message message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin nhắn"));

        if (!message.getSenderId().equals(requesterId)) {
            throw new AccessDeniedException("Chỉ người gửi mới được xoá tin nhắn");
        }

        message.setDeleted(true);
        message.setEditedAt(Instant.now());
        messageRepository.save(message);

        realtimeService.broadcastMessageDeleted(conversationId, messageId);
    }

    // ---- Đã đọc / đang nhập ----

    @Transactional
    public ConversationResponse markRead(UUID conversationId, UUID userId, UUID lastReadMessageId) {
        requireMember(conversationId, userId);

        Message target = lastReadMessageId != null
                ? messageRepository.findByIdAndConversationId(lastReadMessageId, conversationId)
                    .orElseThrow(() -> new NotFoundException("Tin nhắn không thuộc hội thoại này"))
                : messageRepository.findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversationId)
                    .orElse(null);

        if (target == null) {
            // Hội thoại chưa có tin nào — không có gì để đánh dấu.
            return getConversation(conversationId, userId);
        }

        ConversationReadStatus.ReadStatusId id =
                new ConversationReadStatus.ReadStatusId(conversationId, userId);
        ConversationReadStatus status = readStatusRepository.findById(id)
                .orElseGet(() -> new ConversationReadStatus(id, target.getId(), Instant.now()));
        status.setLastReadMessageId(target.getId());
        status.setUpdatedAt(Instant.now());
        readStatusRepository.save(status);

        realtimeService.broadcastReadReceipt(conversationId, userId, target.getId(), status.getUpdatedAt());

        return getConversation(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public long getTotalUnread(UUID userId) {
        List<Conversation> conversations = conversationRepository.findActiveByUserId(userId);
        if (conversations.isEmpty()) return 0;

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, UUID> myLastRead = readStatusRepository
                .findByIdUserIdAndIdConversationIdIn(userId, conversationIds)
                .stream()
                .collect(Collectors.toMap(
                        rs -> rs.getId().getConversationId(),
                        ConversationReadStatus::getLastReadMessageId,
                        (a, b) -> a
                ));

        long total = 0;
        for (UUID conversationId : conversationIds) {
            UUID lastReadId = myLastRead.get(conversationId);
            Instant readAt = lastReadId == null ? null
                    : messageRepository.findById(lastReadId).map(Message::getCreatedAt).orElse(null);
            total += countUnread(conversationId, userId, readAt);
        }
        return total;
    }

    public void sendTyping(UUID conversationId, UUID userId, String username, boolean typing) {
        requireMember(conversationId, userId);
        List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(conversationId);
        for (ConversationMember member : members) {
            if (!member.getUserId().equals(userId)) {
                if (authServiceClient.isMutuallyBlocked(userId, member.getUserId())) {
                    return;
                }
            }
        }
        realtimeService.broadcastTyping(conversationId, userId, username, typing);
    }

    // ---- Helpers ----

    private Conversation requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hội thoại"));
    }

    private void requireMember(UUID conversationId, UUID userId) {
        if (userId == null) {
            throw new AccessDeniedException("Bạn cần đăng nhập để sử dụng chat");
        }
        if (!memberRepository.existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId)) {
            throw new AccessDeniedException("Bạn không phải thành viên của hội thoại này");
        }
    }

    /** Đếm tin chưa đọc — tách nhánh để tránh tham số NULL trong câu JPQL. */
    private long countUnread(UUID conversationId, UUID userId, Instant after) {
        return after == null
                ? messageRepository.countUnreadAll(conversationId, userId)
                : messageRepository.countUnreadAfter(conversationId, userId, after);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Giá trị UUID không hợp lệ: " + value);
        }
    }

    private ConversationResponse enrich(Conversation conversation, UUID viewerId) {
        List<ConversationMember> members = memberRepository.findByConversationIdAndLeftAtIsNull(conversation.getId());
        ConversationMember me = members.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .findFirst()
                .orElse(null);

        List<UUID> partnerIds = members.stream()
                .map(ConversationMember::getUserId)
                .filter(id -> !id.equals(viewerId))
                .distinct()
                .toList();
        Map<UUID, AuthServiceClient.UserSummary> summaries =
                authServiceClient.getUsersSummaryMap(partnerIds);

        UUID lastReadMessageId = readStatusRepository
                .findById(new ConversationReadStatus.ReadStatusId(conversation.getId(), viewerId))
                .map(ConversationReadStatus::getLastReadMessageId)
                .orElse(null);
        Instant readAt = lastReadMessageId == null ? null
                : messageRepository.findById(lastReadMessageId).map(Message::getCreatedAt).orElse(null);

        long unread = countUnread(conversation.getId(), viewerId, readAt);

        return toResponse(conversation, viewerId, members, summaries, unread, lastReadMessageId,
                me != null && me.isMuted());
    }

    private ConversationResponse toResponse(Conversation conversation,
                                            UUID viewerId,
                                            List<ConversationMember> members,
                                            Map<UUID, AuthServiceClient.UserSummary> summaries,
                                            long unreadCount,
                                            UUID lastReadMessageId,
                                            boolean muted) {
        List<String> memberIds = members.stream()
                .map(m -> m.getUserId().toString())
                .collect(Collectors.toList());

        MessageResponse lastMessage = messageRepository
                .findFirstByConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversation.getId())
                .map(this::toMessageResponse)
                .orElse(null);

        PartnerResponse partner = null;
        if ("DIRECT".equalsIgnoreCase(conversation.getType())) {
            Optional<ConversationMember> partnerMember = members.stream()
                    .filter(m -> !m.getUserId().equals(viewerId))
                    .findFirst();
            if (partnerMember.isPresent()) {
                UUID partnerId = partnerMember.get().getUserId();
                AuthServiceClient.UserSummary summary = summaries.get(partnerId);
                partner = PartnerResponse.builder()
                        .id(partnerId.toString())
                        .username(summary != null ? summary.getUsername() : null)
                        .displayName(summary != null ? summary.getDisplayName() : null)
                        .avatarUrl(summary != null ? summary.getAvatarUrl() : null)
                        .verified(summary != null && summary.isVerified())
                        .verifiedUntil(summary != null ? summary.getVerifiedUntil() : null)
                        .build();
            }
        }

        return ConversationResponse.builder()
                .id(conversation.getId().toString())
                .type(conversation.getType())
                .name(conversation.getName())
                .avatarUrl(conversation.getAvatarUrl())
                .memberIds(memberIds)
                .partner(partner)
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .lastReadMessageId(lastReadMessageId != null ? lastReadMessageId.toString() : null)
                .muted(muted)
                .updatedAt(conversation.getUpdatedAt())
                .createdAt(conversation.getCreatedAt())
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
