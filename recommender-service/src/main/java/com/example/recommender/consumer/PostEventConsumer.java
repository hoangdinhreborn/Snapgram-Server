package com.example.recommender.consumer;

import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.repository.RecommenderPostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostEventConsumer {

    private final RecommenderPostViewRepository postViewRepository;

    @KafkaListener(topics = "content.post-created", groupId = "recommender-service")
    @Transactional
    public void handlePostCreated(Map<String, Object> payload) {
        try {
            String postIdStr = (String) payload.get("postId");
            String authorIdStr = (String) payload.get("authorId");
            if (postIdStr == null || authorIdStr == null) return;

            UUID postId = UUID.fromString(postIdStr);
            UUID authorId = UUID.fromString(authorIdStr);
            String contentType = (String) payload.getOrDefault("contentType", "IMAGE");
            String caption = (String) payload.get("caption");
            String visibility = (String) payload.getOrDefault("visibility", "PUBLIC");

            // Format tags from hashtags list if present
            String tags = null;
            Object hashtagsObj = payload.get("hashtags");
            if (hashtagsObj instanceof List<?> list) {
                tags = String.join(",", list.stream().map(Object::toString).toList());
            }

            Instant createdAt = Instant.now();
            Object createdAtObj = payload.get("createdAt");
            if (createdAtObj instanceof String s) {
                createdAt = Instant.parse(s);
            }

            RecommenderPostView view = RecommenderPostView.builder()
                    .postId(postId)
                    .authorId(authorId)
                    .contentType(contentType)
                    .caption(caption)
                    .tags(tags)
                    .visibility(visibility)
                    .authorIsPrivate(false)
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .build();

            postViewRepository.save(view);
            log.info("Synced recommender_post_view for new post: {}", postId);
        } catch (Exception e) {
            log.warn("Failed to process content.post-created event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "content.post-updated", groupId = "recommender-service")
    @Transactional
    public void handlePostUpdated(Map<String, Object> payload) {
        try {
            String postIdStr = (String) payload.get("postId");
            if (postIdStr == null) return;
            UUID postId = UUID.fromString(postIdStr);

            postViewRepository.findById(postId).ifPresent(view -> {
                if (payload.containsKey("caption")) {
                    view.setCaption((String) payload.get("caption"));
                }
                if (payload.containsKey("visibility")) {
                    view.setVisibility((String) payload.get("visibility"));
                }
                view.setUpdatedAt(Instant.now());
                postViewRepository.save(view);
                log.info("Updated recommender_post_view for post: {}", postId);
            });
        } catch (Exception e) {
            log.warn("Failed to process content.post-updated event: {}", e.getMessage());
        }
    }
}
