package com.example.content.service;

import com.example.content.dto.FollowResponse;
import com.example.content.entity.Follow;
import com.example.content.entity.FollowStatus;
import com.example.content.entity.UserPrivacyView;
import com.example.content.event.FollowEvent;
import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.exception.DuplicateResourceException;
import com.example.content.repository.FollowRepository;
import com.example.content.repository.UserPrivacyViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FollowService {

    private static final String TOPIC_FOLLOW = "follow.events";

    private final FollowRepository followRepository;
    private final UserPrivacyViewRepository userPrivacyViewRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public FollowResponse follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("You cannot follow yourself");
        }

        if (followRepository.findByFollowerIdAndFollowingId(followerId, followingId).isPresent()) {
            throw new DuplicateResourceException("Already following or request pending");
        }

        // Check target user's privacy setting
        boolean isPrivate = userPrivacyViewRepository.findById(followingId)
                .map(UserPrivacyView::isPrivate)
                .orElse(false);

        FollowStatus status = isPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED;

        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        follow.setStatus(status);
        Follow saved = followRepository.save(follow);

        if (status == FollowStatus.ACCEPTED) {
            publishFollowEvent(saved);
        }

        return toResponse(saved);
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(() -> new ContentNotFoundException("Follow relationship not found"));
        followRepository.delete(follow);
    }

    @Transactional
    public FollowResponse acceptRequest(UUID followId, UUID followingId) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new ContentNotFoundException("Follow request not found"));

        if (!follow.getFollowingId().equals(followingId)) {
            throw new AccessDeniedException("This is not your follow request");
        }
        if (follow.getStatus() != FollowStatus.PENDING) {
            throw new IllegalArgumentException("Request is not pending");
        }

        follow.setStatus(FollowStatus.ACCEPTED);
        Follow saved = followRepository.save(follow);
        publishFollowEvent(saved);
        return toResponse(saved);
    }

    @Transactional
    public void rejectRequest(UUID followId, UUID followingId) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new ContentNotFoundException("Follow request not found"));

        if (!follow.getFollowingId().equals(followingId)) {
            throw new AccessDeniedException("This is not your follow request");
        }
        follow.setStatus(FollowStatus.REJECTED);
        followRepository.save(follow);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowers(UUID userId, Pageable pageable) {
        return followRepository.findByFollowingIdAndStatus(userId, FollowStatus.ACCEPTED, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getFollowing(UUID userId, Pageable pageable) {
        return followRepository.findByFollowerIdAndStatus(userId, FollowStatus.ACCEPTED, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<FollowResponse> getPendingRequests(UUID userId, Pageable pageable) {
        return followRepository.findByFollowingIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.PENDING, pageable)
                .map(this::toResponse);
    }

    private void publishFollowEvent(Follow follow) {
        try {
            kafkaTemplate.send(TOPIC_FOLLOW, follow.getFollowerId().toString(), FollowEvent.builder()
                    .followerId(follow.getFollowerId().toString())
                    .followingId(follow.getFollowingId().toString())
                    .status(follow.getStatus().name())
                    .createdAt(follow.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish follow event: {}", e.getMessage());
        }
    }

    private FollowResponse toResponse(Follow f) {
        return FollowResponse.builder()
                .id(f.getId().toString())
                .followerId(f.getFollowerId().toString())
                .followingId(f.getFollowingId().toString())
                .status(f.getStatus().name())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
