package com.example.content.service;

import com.example.content.dto.StoryResponse;
import com.example.content.dto.CreateStoryRequest;
import com.example.content.dto.StoryViewerResponse;
import com.example.content.entity.*;
import com.example.content.event.StoryCreatedEvent;
import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoryService {

    private static final String TOPIC_STORY_CREATED = "content.story-created";

    @Value("${app.content.story-ttl-hours:24}")
    private int storyTtlHours;

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final FollowRepository followRepository;
    private final CloseFriendRepository closeFriendRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public StoryResponse createStory(UUID authorId, CreateStoryRequest request) {
        StoryMediaType mediaType;
        try {
            mediaType = StoryMediaType.valueOf(request.getMediaType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mediaType: " + request.getMediaType());
        }

        Visibility visibility;
        try {
            visibility = Visibility.valueOf(request.getVisibility() != null ? request.getVisibility() : "FOLLOWERS");
        } catch (IllegalArgumentException e) {
            visibility = Visibility.FOLLOWERS;
        }

        Story story = new Story();
        story.setAuthorId(authorId);
        story.setMediaId(request.getMediaId());
        story.setMediaUrl(request.getMediaUrl());
        story.setMediaType(mediaType);
        story.setCaption(request.getCaption());
        story.setVisibility(visibility);
        story.setExpiresAt(Instant.now().plus(storyTtlHours, ChronoUnit.HOURS));

        Story saved = storyRepository.save(story);

        try {
            kafkaTemplate.send(TOPIC_STORY_CREATED, saved.getId().toString(), StoryCreatedEvent.builder()
                    .storyId(saved.getId().toString())
                    .authorId(authorId.toString())
                    .mediaUrl(saved.getMediaUrl())
                    .mediaType(mediaType.name())
                    .visibility(visibility.name())
                    .expiresAt(saved.getExpiresAt())
                    .createdAt(saved.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish story-created event: {}", e.getMessage());
        }

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public StoryResponse getStory(UUID storyId, UUID requesterId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ContentNotFoundException("Story not found"));

        if (story.getExpiresAt().isBefore(Instant.now()) && !story.getAuthorId().equals(requesterId)) {
            throw new ContentNotFoundException("Story has expired");
        }

        checkStoryVisibility(story, requesterId);
        boolean viewedByMe = storyViewRepository.existsByIdStoryIdAndIdViewerId(storyId, requesterId);
        return toResponse(story, viewedByMe);
    }

    @Transactional(readOnly = true)
    public Page<StoryResponse> getStoryFeed(UUID userId, Pageable pageable) {
        List<UUID> followingIds = followRepository.findAcceptedFollowingIds(userId);
        List<UUID> authorIds = new ArrayList<>(followingIds);
        authorIds.add(userId);

        List<Visibility> visibilities = List.of(Visibility.PUBLIC, Visibility.FOLLOWERS);
        return storyRepository.findActiveStoriesForFeed(authorIds, Instant.now(), visibilities, pageable)
                .map(s -> toResponse(s, storyViewRepository.existsByIdStoryIdAndIdViewerId(s.getId(), userId)));
    }

    @Transactional
    public void deleteStory(UUID storyId, UUID authorId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ContentNotFoundException("Story not found"));

        if (!story.getAuthorId().equals(authorId)) {
            throw new AccessDeniedException("You cannot delete this story");
        }

        storyRepository.delete(story);
    }

    @Transactional
    public void viewStory(UUID storyId, UUID viewerId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ContentNotFoundException("Story not found"));

        if (story.getExpiresAt().isBefore(Instant.now())) return;
        if (story.getAuthorId().equals(viewerId)) return; // Don't record author's own view

        if (!storyViewRepository.existsByIdStoryIdAndIdViewerId(storyId, viewerId)) {
            StoryView.StoryViewId svId = new StoryView.StoryViewId();
            svId.setStoryId(storyId);
            svId.setViewerId(viewerId);
            StoryView sv = new StoryView();
            sv.setId(svId);
            storyViewRepository.save(sv);
        }
    }

    @Transactional(readOnly = true)
    public Page<StoryViewerResponse> getViewers(UUID storyId, UUID authorId, Pageable pageable) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ContentNotFoundException("Story not found"));

        if (!story.getAuthorId().equals(authorId)) {
            throw new AccessDeniedException("Only the story author can see viewers");
        }

        return storyViewRepository.findByIdStoryIdOrderByViewedAtDesc(storyId, pageable)
                .map(sv -> StoryViewerResponse.builder()
                        .viewerId(sv.getId().getViewerId().toString())
                        .viewedAt(sv.getViewedAt())
                        .build());
    }

    private void checkStoryVisibility(Story story, UUID requesterId) {
        if (story.getAuthorId().equals(requesterId)) return;
        if (story.getVisibility() == Visibility.PRIVATE) {
            throw new AccessDeniedException("This story is private");
        }
        if (story.getVisibility() == Visibility.FOLLOWERS) {
            boolean isFollowing = followRepository.existsByFollowerIdAndFollowingIdAndStatus(
                    requesterId, story.getAuthorId(), FollowStatus.ACCEPTED);
            if (!isFollowing) throw new AccessDeniedException("This story is for followers only");
        }
        if (story.getVisibility() == Visibility.CLOSE_FRIENDS) {
            boolean isCloseFriend = closeFriendRepository.existsByIdOwnerIdAndIdFriendId(
                    story.getAuthorId(), requesterId);
            if (!isCloseFriend) throw new AccessDeniedException("This story is for close friends only");
        }
    }

    private StoryResponse toResponse(Story s, boolean viewedByMe) {
        return StoryResponse.builder()
                .id(s.getId().toString())
                .authorId(s.getAuthorId().toString())
                .mediaId(s.getMediaId() != null ? s.getMediaId().toString() : null)
                .mediaUrl(s.getMediaUrl())
                .mediaType(s.getMediaType().name())
                .caption(s.getCaption())
                .visibility(s.getVisibility().name())
                .viewedByMe(viewedByMe)
                .createdAt(s.getCreatedAt())
                .expiresAt(s.getExpiresAt())
                .build();
    }
}
