package com.example.chat.service;

import com.example.chat.client.AuthServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.events", groupId = "chat-service")
    public void consumeUserEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            JsonNode payload = root.has("data") ? root.get("data") : root;
            String eventType = payload.has("eventType") ? payload.get("eventType").asText() : null;
            if ("USER_BLOCKED".equals(eventType) || "USER_UNBLOCKED".equals(eventType)) {
                String blockerIdStr = payload.has("blockerId") ? payload.get("blockerId").asText() : null;
                String blockedIdStr = payload.has("blockedId") ? payload.get("blockedId").asText() : null;
                if (blockerIdStr != null && blockedIdStr != null) {
                    authServiceClient.evictBlockCache(UUID.fromString(blockerIdStr), UUID.fromString(blockedIdStr));
                    log.debug("Evicted block cache in chat-service for users {} and {}", blockerIdStr, blockedIdStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to process user.events in chat-service: {}", e.getMessage());
        }
    }
}
