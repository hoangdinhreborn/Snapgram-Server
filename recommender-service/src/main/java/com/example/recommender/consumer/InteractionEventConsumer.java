package com.example.recommender.consumer;

import com.example.recommender.entity.RecommenderInteractionView;
import com.example.recommender.repository.RecommenderInteractionViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionEventConsumer {

    private final RecommenderInteractionViewRepository interactionViewRepository;

    @KafkaListener(topics = "content.interaction", groupId = "recommender-service")
    @Transactional
    public void handleInteraction(Map<String, Object> payload) {
        try {
            String userIdStr = (String) payload.get("userId");
            String postIdStr = (String) payload.get("postId");
            String type = (String) payload.get("type");
            if (userIdStr == null || postIdStr == null || type == null) return;

            UUID userId = UUID.fromString(userIdStr);
            UUID postId = UUID.fromString(postIdStr);

            BigDecimal watchTimeRatio = null;
            Object wtrObj = payload.get("watchTimeRatio");
            if (wtrObj instanceof Number n) {
                watchTimeRatio = BigDecimal.valueOf(n.doubleValue());
            }

            BigDecimal explicitRating = null;
            Object erObj = payload.get("explicitRating");
            if (erObj instanceof Number n) {
                explicitRating = BigDecimal.valueOf(n.doubleValue());
            }

            Instant createdAt = Instant.now();
            Object caObj = payload.get("createdAt");
            if (caObj instanceof String s) {
                createdAt = Instant.parse(s);
            }

            RecommenderInteractionView view = RecommenderInteractionView.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .postId(postId)
                    .type(type)
                    .watchTimeRatio(watchTimeRatio)
                    .explicitRating(explicitRating)
                    .createdAt(createdAt)
                    .build();

            interactionViewRepository.save(view);
            log.info("Synced interaction for user {} on post {}: type={}", userId, postId, type);
        } catch (Exception e) {
            log.warn("Failed to process content.interaction event: {}", e.getMessage());
        }
    }
}
