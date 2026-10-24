package com.example.chat.messaging;

import com.example.chat.entity.ConversationMember;
import com.example.chat.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phát event chat.messages để notification-service tạo thông báo (badge + toast).
 *
 * Shape phải khớp com.example.notification.event.ChatMessageEvent:
 * { senderId, receiverId, conversationId, messageId, muted, createdAt }
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEventPublisher {

    private static final String TOPIC_CHAT_MESSAGES = "chat.messages";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Mỗi người nhận một event riêng (DIRECT: 1 event; GROUP: n-1 event).
     * `muted` lấy theo trạng thái mute của chính người nhận.
     */
    public void publishMessageCreated(Message message, UUID senderId, List<ConversationMember> members) {
        if (message == null || members == null || members.isEmpty()) return;

        for (ConversationMember member : members) {
            UUID receiverId = member.getUserId();
            if (receiverId == null || receiverId.equals(senderId)) continue;

            Map<String, Object> event = new LinkedHashMap<>();
            // eventId ổn định theo (messageId, receiverId) để retry không tạo thông báo trùng
            event.put("eventId", UUID.nameUUIDFromBytes(
                    (message.getId() + ":" + receiverId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            event.put("senderId", senderId.toString());
            event.put("receiverId", receiverId.toString());
            event.put("conversationId", message.getConversationId().toString());
            event.put("messageId", message.getId().toString());
            event.put("muted", member.isMuted());
            event.put("createdAt", (message.getCreatedAt() != null ? message.getCreatedAt() : Instant.now()).toString());

            try {
                kafkaTemplate.send(TOPIC_CHAT_MESSAGES, message.getConversationId().toString(), event)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.warn("Không gửi được chat.messages cho {}: {}", receiverId, ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                // Không để lỗi Kafka làm hỏng việc gửi tin nhắn.
                log.warn("Bỏ qua publish chat.messages ({}): {}", receiverId, e.getMessage());
            }
        }
    }
}
