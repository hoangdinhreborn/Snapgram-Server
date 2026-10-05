package com.example.recommender.consumer;

import com.example.recommender.entity.RecommenderBlockView;
import com.example.recommender.entity.RecommenderMuteView;
import com.example.recommender.repository.RecommenderBlockViewRepository;
import com.example.recommender.repository.RecommenderMuteViewRepository;
import com.example.recommender.repository.RecommenderPostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final RecommenderPostViewRepository postViewRepository;
    private final RecommenderBlockViewRepository blockViewRepository;
    private final RecommenderMuteViewRepository muteViewRepository;

    @KafkaListener(topics = "user.events", groupId = "recommender-service")
    @Transactional
    public void handleUserEvent(Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("eventType");
            if (eventType == null) return;

            switch (eventType) {
                case "PRIVACY_CHANGED" -> {
                    String userIdStr = (String) payload.get("userId");
                    Object isPrivateObj = payload.get("isPrivate");
                    if (userIdStr != null && isPrivateObj != null) {
                        UUID userId = UUID.fromString(userIdStr);
                        boolean isPrivate = Boolean.TRUE.equals(isPrivateObj);
                        postViewRepository.updateAuthorPrivacy(userId, isPrivate);
                        log.info("Updated author privacy for user {}: isPrivate={}", userId, isPrivate);
                    }
                }
                case "USER_BLOCKED" -> {
                    String blockerIdStr = (String) payload.get("blockerId");
                    String blockedIdStr = (String) payload.get("blockedId");
                    if (blockerIdStr != null && blockedIdStr != null) {
                        UUID blockerId = UUID.fromString(blockerIdStr);
                        UUID blockedId = UUID.fromString(blockedIdStr);
                        RecommenderBlockView block = RecommenderBlockView.builder()
                                .id(new RecommenderBlockView.RecommenderBlockId(blockerId, blockedId))
                                .updatedAt(Instant.now())
                                .build();
                        blockViewRepository.save(block);
                        log.info("Synced block view: {} blocked {}", blockerId, blockedId);
                    }
                }
                case "USER_UNBLOCKED" -> {
                    String blockerIdStr = (String) payload.get("blockerId");
                    String blockedIdStr = (String) payload.get("blockedId");
                    if (blockerIdStr != null && blockedIdStr != null) {
                        UUID blockerId = UUID.fromString(blockerIdStr);
                        UUID blockedId = UUID.fromString(blockedIdStr);
                        blockViewRepository.deleteByIdBlockerIdAndIdBlockedId(blockerId, blockedId);
                        log.info("Removed block view: {} unblocked {}", blockerId, blockedId);
                    }
                }
                case "USER_MUTED" -> {
                    String muterIdStr = (String) payload.get("muterId");
                    String mutedIdStr = (String) payload.get("mutedId");
                    if (muterIdStr != null && mutedIdStr != null) {
                        UUID muterId = UUID.fromString(muterIdStr);
                        UUID mutedId = UUID.fromString(mutedIdStr);
                        RecommenderMuteView mute = RecommenderMuteView.builder()
                                .id(new RecommenderMuteView.RecommenderMuteId(muterId, mutedId))
                                .updatedAt(Instant.now())
                                .build();
                        muteViewRepository.save(mute);
                        log.info("Synced mute view: {} muted {}", muterId, mutedId);
                    }
                }
                case "USER_UNMUTED" -> {
                    String muterIdStr = (String) payload.get("muterId");
                    String mutedIdStr = (String) payload.get("mutedId");
                    if (muterIdStr != null && mutedIdStr != null) {
                        UUID muterId = UUID.fromString(muterIdStr);
                        UUID mutedId = UUID.fromString(mutedIdStr);
                        muteViewRepository.deleteByIdMuterIdAndIdMutedId(muterId, mutedId);
                        log.info("Removed mute view: {} unmuted {}", muterId, mutedId);
                    }
                }
                default -> log.debug("Ignored user event type: {}", eventType);
            }
        } catch (Exception e) {
            log.warn("Failed to process user.events event: {}", e.getMessage());
        }
    }
}
