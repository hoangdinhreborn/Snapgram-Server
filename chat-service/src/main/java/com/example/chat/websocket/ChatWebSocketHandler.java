package com.example.chat.websocket;

import com.example.chat.service.ChatRealtimeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket cho chat tại /ws/chat.
 *
 * Chỉ chấp nhận kết nối đã được xác thực từ API Gateway,
 * nhận diện người dùng qua header nội bộ X-User-Id (và X-Username).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String ATTR_USER_ID = "chat.userId";
    private static final String ATTR_USERNAME = "chat.username";

    private final ChatSessionRegistry sessionRegistry;
    private final ChatRealtimeService realtimeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** userId của từng session — dùng khi đóng kết nối. */
    private final Map<String, UUID> sessionUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID userId = parseUserId(session);
        if (userId == null) {
            log.warn("Đóng WS chat vì thiếu hoặc sai header X-User-Id: uri={}", session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized: Missing or invalid X-User-Id"));
            return;
        }

        String username = session.getHandshakeHeaders().getFirst("X-Username");
        session.getAttributes().put(ATTR_USER_ID, userId);
        if (username != null && !username.isBlank()) {
            session.getAttributes().put(ATTR_USERNAME, username);
        }

        sessionUsers.put(session.getId(), userId);
        sessionRegistry.register(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = sessionUsers.get(session.getId());
        if (userId == null) return;

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText("");
            if (!"TYPING".equalsIgnoreCase(type)) {
                // Các loại khác đi qua REST để đảm bảo dữ liệu được lưu bền.
                return;
            }

            String conversationIdRaw = root.path("conversationId").asText(null);
            if (conversationIdRaw == null) return;

            UUID conversationId;
            try {
                conversationId = UUID.fromString(conversationIdRaw);
            } catch (IllegalArgumentException e) {
                return;
            }

            boolean typing = root.path("typing").asBoolean(true);
            Object usernameAttr = session.getAttributes().get(ATTR_USERNAME);
            String username = usernameAttr != null ? usernameAttr.toString() : null;

            realtimeService.broadcastTyping(conversationId, userId, username, typing);
        } catch (Exception e) {
            log.debug("Bỏ qua WS message không hợp lệ từ {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = sessionUsers.remove(session.getId());
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Lỗi transport WS chat session {}: {}", session.getId(), exception.getMessage());
        UUID userId = sessionUsers.remove(session.getId());
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    private UUID parseUserId(WebSocketSession session) {
        String raw = session.getHandshakeHeaders().getFirst("X-User-Id");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-User-Id header format: {}", raw);
            return null;
        }
    }
}
