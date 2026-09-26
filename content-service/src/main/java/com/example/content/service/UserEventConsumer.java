package com.example.content.service;

import com.example.content.entity.UserPrivacyView;
import com.example.content.repository.UserPrivacyViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to user.events from auth-service to sync privacy settings.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserPrivacyViewRepository userPrivacyViewRepository;

    @KafkaListener(topics = "user.events", groupId = "content-service")
    @Transactional
    public void handleUserEvent(Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("eventType");
            if (!"PRIVACY_CHANGED".equals(eventType)) return;

            String userIdStr = (String) payload.get("userId");
            Object isPrivateObj = payload.get("isPrivate");

            if (userIdStr == null || isPrivateObj == null) return;

            UUID userId = UUID.fromString(userIdStr);
            boolean isPrivate = Boolean.TRUE.equals(isPrivateObj);

            UserPrivacyView view = userPrivacyViewRepository.findById(userId)
                    .orElseGet(() -> {
                        UserPrivacyView v = new UserPrivacyView();
                        v.setUserId(userId);
                        return v;
                    });
            view.setPrivate(isPrivate);
            userPrivacyViewRepository.save(view);

            log.info("Updated privacy for user {}: isPrivate={}", userId, isPrivate);
        } catch (Exception e) {
            log.warn("Failed to process user.events payload: {}", e.getMessage());
        }
    }
}
