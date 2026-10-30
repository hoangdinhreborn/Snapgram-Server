package com.example.chat.service;

import com.example.chat.dto.message.MessageResponse;
import com.example.chat.repository.ConversationMemberRepository;
import com.example.chat.websocket.ChatSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đẩy sự kiện chat realtime xuống client qua WebSocket.
 *
 * Envelope thống nhất: { "type": "...", "data": { ... } }
 *
 * TODO(scale): registry đang in-memory nên chỉ đúng khi chạy 1 instance.
 * Khi scale ngang: publish các event này lên Redis pub/sub để mọi instance cùng phát.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRealtimeService {

    public static final String TYPE_MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String TYPE_MESSAGE_DELETED = "MESSAGE_DELETED";
    public static final String TYPE_TYPING = "TYPING";
    public static final String TYPE_READ_RECEIPT = "READ_RECEIPT";

    private final ChatSessionRegistry sessionRegistry;
    private final ConversationMemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    /** Broadcast tin nhắn mới tới mọi thành viên còn hoạt động, gồm cả người gửi (đồng bộ tab khác). */
    public void broadcastMessage(MessageResponse message) {
        UUID conversationId = safeUuid(message.getConversationId());
        if (conversationId == null) return;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", message);
        data.put("conversationId", message.getConversationId());
        emit(activeMemberIds(conversationId), TYPE_MESSAGE_CREATED, data);
    }

    /** Broadcast trạng thái "đang nhập" tới các thành viên khác. */
    public void broadcastTyping(UUID conversationId, UUID senderId, String username, boolean typing) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId.toString());
        data.put("userId", senderId.toString());
        data.put("username", username);
        data.put("typing", typing);

        List<UUID> targets = activeMemberIds(conversationId).stream()
                .filter(id -> !id.equals(senderId))
                .toList();
        emit(targets, TYPE_TYPING, data);
    }

    /** Báo cho đối phương biết đã đọc tới tin nào. */
    public void broadcastReadReceipt(UUID conversationId, UUID readerId, UUID lastReadMessageId, Instant readAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId.toString());
        data.put("userId", readerId.toString());
        data.put("lastReadMessageId", lastReadMessageId != null ? lastReadMessageId.toString() : null);
        data.put("readAt", (readAt != null ? readAt : Instant.now()).toString());

        List<UUID> targets = activeMemberIds(conversationId).stream()
                .filter(id -> !id.equals(readerId))
                .toList();
        emit(targets, TYPE_READ_RECEIPT, data);
    }

    /** Báo tin nhắn đã bị xoá. */
    public void broadcastMessageDeleted(UUID conversationId, UUID messageId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId.toString());
        data.put("messageId", messageId.toString());
        emit(activeMemberIds(conversationId), TYPE_MESSAGE_DELETED, data);
    }

    private List<UUID> activeMemberIds(UUID conversationId) {
        return memberRepository.findByConversationIdAndLeftAtIsNull(conversationId).stream()
                .map(m -> m.getUserId())
                .toList();
    }

    private void emit(List<UUID> userIds, String type, Map<String, Object> data) {
        if (userIds == null || userIds.isEmpty()) return;

        String payload;
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", type);
            envelope.put("data", data);
            payload = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("Không serialize được sự kiện {}: {}", type, e.getMessage());
            return;
        }

        sessionRegistry.sendToUsers(userIds, payload);
    }

    private UUID safeUuid(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
