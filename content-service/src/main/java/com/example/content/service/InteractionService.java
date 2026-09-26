package com.example.content.service;

import com.example.content.dto.InteractionRequest;
import com.example.content.entity.*;
import com.example.content.event.ContentInteractionEvent;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.InteractionRepository;
import com.example.content.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InteractionService {

    private static final String TOPIC_INTERACTION = "content.interaction";

    private final InteractionRepository interactionRepository;
    private final PostRepository postRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void interact(UUID postId, UUID userId, InteractionRequest request) {
        postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        InteractionType type;
        try {
            type = InteractionType.valueOf(request.getType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid interaction type: " + request.getType());
        }

        // For LIKE: enforce uniqueness via DB constraint; idempotent check here
        if (type == InteractionType.LIKE) {
            if (interactionRepository.existsByUserIdAndPostIdAndType(userId, postId, type)) {
                return; // Already liked, idempotent
            }
            postRepository.incrementLikeCount(postId);
        } else if (type == InteractionType.VIEW) {
            postRepository.incrementViewCount(postId);
        }

        Interaction interaction = new Interaction();
        interaction.setUserId(userId);
        interaction.setPostId(postId);
        interaction.setType(type);
        interaction.setWatchTimeRatio(request.getWatchTimeRatio());
        interaction.setExplicitRating(request.getExplicitRating());
        interactionRepository.save(interaction);

        try {
            kafkaTemplate.send(TOPIC_INTERACTION, postId.toString(), ContentInteractionEvent.builder()
                    .userId(userId.toString())
                    .postId(postId.toString())
                    .type(type.name())
                    .watchTimeRatio(request.getWatchTimeRatio() != null
                            ? request.getWatchTimeRatio().doubleValue() : null)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish interaction event: {}", e.getMessage());
        }
    }

    @Transactional
    public void unlike(UUID postId, UUID userId) {
        if (!interactionRepository.existsByUserIdAndPostIdAndType(userId, postId, InteractionType.LIKE)) {
            return; // Idempotent
        }
        interactionRepository.deleteByUserIdAndPostIdAndType(userId, postId, InteractionType.LIKE);
        postRepository.decrementLikeCount(postId);
    }
}
