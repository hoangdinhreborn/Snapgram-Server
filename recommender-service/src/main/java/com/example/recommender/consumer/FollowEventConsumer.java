package com.example.recommender.consumer;

import com.example.recommender.entity.RecommenderFollowView;
import com.example.recommender.repository.RecommenderFollowViewRepository;
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
public class FollowEventConsumer {

    private final RecommenderFollowViewRepository followViewRepository;

    @KafkaListener(topics = "follow.events", groupId = "recommender-service")
    @Transactional
    public void handleFollowEvent(Map<String, Object> payload) {
        try {
            String followerIdStr = (String) payload.get("followerId");
            String followingIdStr = (String) payload.get("followingId");
            String status = (String) payload.get("status");
            if (followerIdStr == null || followingIdStr == null) return;

            UUID followerId = UUID.fromString(followerIdStr);
            UUID followingId = UUID.fromString(followingIdStr);

            if ("UNFOLLOW".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                followViewRepository.deleteByIdFollowerIdAndIdFollowingId(followerId, followingId);
                log.info("Removed follow view: {} -> {}", followerId, followingId);
            } else if ("ACCEPTED".equalsIgnoreCase(status)) {
                RecommenderFollowView view = RecommenderFollowView.builder()
                        .id(new RecommenderFollowView.RecommenderFollowId(followerId, followingId))
                        .status("ACCEPTED")
                        .createdAt(Instant.now())
                        .build();
                followViewRepository.save(view);
                log.info("Saved follow view: {} -> {}", followerId, followingId);
            }
        } catch (Exception e) {
            log.warn("Failed to process follow.events event: {}", e.getMessage());
        }
    }
}
