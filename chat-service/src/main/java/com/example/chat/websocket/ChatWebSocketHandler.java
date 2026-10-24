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

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket cho chat tại /ws/chat.
 *
 * Gateway bỏ qua kiểm tra JWT cho path /ws/ (trình duyệt không set được header
 * handshake), nên userId lấy từ query param: /ws/chat?userId=<uuid>.
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
            log.debug("Đóng WS chat vì thiếu userId: {}", session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing userId"));
            return;
        }

        String username = parseQueryParam(session, "username");
        session.getAttributes().put(ATTR_USER_ID, userId);
        if (username != null) {
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
        String raw = parseQueryParam(session, "userId");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String parseQueryParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;

        for (String part : uri.getQuery().split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String key = part.substring(0, idx);
            if (!name.equals(key)) continue;
            String value = part.substring(idx + 1);
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }
}
