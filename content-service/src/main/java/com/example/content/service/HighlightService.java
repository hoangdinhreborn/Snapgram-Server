package com.example.content.service;

import com.example.content.dto.CreateHighlightRequest;
import com.example.content.dto.HighlightDetailResponse;
import com.example.content.dto.HighlightResponse;
import com.example.content.dto.StoryResponse;
import com.example.content.entity.Highlight;
import com.example.content.entity.HighlightItem;
import com.example.content.entity.Story;
import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.HighlightItemRepository;
import com.example.content.repository.HighlightRepository;
import com.example.content.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HighlightService {

    private final HighlightRepository highlightRepository;
    private final HighlightItemRepository highlightItemRepository;
    private final StoryRepository storyRepository;
    private final com.example.content.client.AuthServiceClient authServiceClient;

    @Transactional
    public HighlightResponse createHighlight(UUID userId, CreateHighlightRequest request) {
        Highlight highlight = new Highlight();
        highlight.setUserId(userId);
        highlight.setName(request.getName().trim());

        List<UUID> storyIds = request.getStoryIds() != null ? request.getStoryIds() : Collections.emptyList();

        UUID coverStoryId = request.getCoverStoryId();
        if (coverStoryId == null && !storyIds.isEmpty()) {
            coverStoryId = storyIds.get(0);
        }
        highlight.setCoverStoryId(coverStoryId);

        Highlight saved = highlightRepository.save(highlight);

        for (UUID storyId : storyIds) {
            HighlightItem.HighlightItemId id = new HighlightItem.HighlightItemId();
            id.setHighlightId(saved.getId());
            id.setStoryId(storyId);

            HighlightItem item = new HighlightItem();
            item.setId(id);
            highlightItemRepository.save(item);
        }

        String coverMediaUrl = resolveCoverMediaUrl(saved.getCoverStoryId());

        return HighlightResponse.builder()
                .id(saved.getId().toString())
                .userId(saved.getUserId().toString())
                .name(saved.getName())
                .coverStoryId(saved.getCoverStoryId() != null ? saved.getCoverStoryId().toString() : null)
                .coverMediaUrl(coverMediaUrl)
                .storyCount(storyIds.size())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<HighlightResponse> getUserHighlights(UUID userId, UUID viewerId) {
        if (viewerId != null && authServiceClient.isMutuallyBlocked(viewerId, userId)) {
            return Collections.emptyList();
        }
        List<Highlight> highlights = highlightRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return highlights.stream().map(h -> {
            List<HighlightItem> items = highlightItemRepository.findByIdHighlightIdOrderByCreatedAtDesc(h.getId());
            String coverMediaUrl = resolveCoverMediaUrl(h.getCoverStoryId());
            if (coverMediaUrl == null && !items.isEmpty()) {
                coverMediaUrl = resolveCoverMediaUrl(items.get(0).getId().getStoryId());
            }

            return HighlightResponse.builder()
                    .id(h.getId().toString())
                    .userId(h.getUserId().toString())
                    .name(h.getName())
                    .coverStoryId(h.getCoverStoryId() != null ? h.getCoverStoryId().toString() : null)
                    .coverMediaUrl(coverMediaUrl)
                    .storyCount(items.size())
                    .createdAt(h.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HighlightDetailResponse getHighlightDetail(UUID highlightId, UUID viewerId) {
        Highlight highlight = highlightRepository.findById(highlightId)
                .orElseThrow(() -> new ContentNotFoundException("Highlight not found"));

        if (viewerId != null && authServiceClient.isMutuallyBlocked(viewerId, highlight.getUserId())) {
            throw new ContentNotFoundException("Highlight not found");
        }

        List<HighlightItem> items = highlightItemRepository.findByIdHighlightIdOrderByCreatedAtDesc(highlightId);

        List<StoryResponse> stories = new ArrayList<>();
        for (HighlightItem item : items) {
            storyRepository.findById(item.getId().getStoryId()).ifPresent(s -> {
                stories.add(StoryResponse.builder()
                        .id(s.getId().toString())
                        .authorId(s.getAuthorId().toString())
                        .mediaId(s.getMediaId() != null ? s.getMediaId().toString() : null)
                        .mediaUrl(s.getMediaUrl())
                        .mediaType(s.getMediaType().name())
                        .caption(s.getCaption())
                        .visibility(s.getVisibility().name())
                        .viewedByMe(true)
                        .createdAt(s.getCreatedAt())
                        .expiresAt(s.getExpiresAt())
                        .build());
            });
        }

        String coverMediaUrl = resolveCoverMediaUrl(highlight.getCoverStoryId());
        if (coverMediaUrl == null && !stories.isEmpty()) {
            coverMediaUrl = stories.get(0).getMediaUrl();
        }

        return HighlightDetailResponse.builder()
                .id(highlight.getId().toString())
                .userId(highlight.getUserId().toString())
                .name(highlight.getName())
                .coverStoryId(highlight.getCoverStoryId() != null ? highlight.getCoverStoryId().toString() : null)
                .coverMediaUrl(coverMediaUrl)
                .storyCount(stories.size())
                .createdAt(highlight.getCreatedAt())
                .stories(stories)
                .build();
    }

    @Transactional
    public void addStoryToHighlight(UUID highlightId, UUID storyId, UUID userId) {
        Highlight highlight = highlightRepository.findById(highlightId)
                .orElseThrow(() -> new ContentNotFoundException("Highlight not found"));

        if (!highlight.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not allowed to edit this highlight");
        }

        HighlightItem.HighlightItemId id = new HighlightItem.HighlightItemId();
        id.setHighlightId(highlightId);
        id.setStoryId(storyId);

        if (!highlightItemRepository.existsById(id)) {
            HighlightItem item = new HighlightItem();
            item.setId(id);
            highlightItemRepository.save(item);
        }

        if (highlight.getCoverStoryId() == null) {
            highlight.setCoverStoryId(storyId);
            highlightRepository.save(highlight);
        }
    }

    @Transactional
    public void removeStoryFromHighlight(UUID highlightId, UUID storyId, UUID userId) {
        Highlight highlight = highlightRepository.findById(highlightId)
                .orElseThrow(() -> new ContentNotFoundException("Highlight not found"));

        if (!highlight.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not allowed to edit this highlight");
        }

        highlightItemRepository.deleteByIdHighlightIdAndIdStoryId(highlightId, storyId);

        if (storyId.equals(highlight.getCoverStoryId())) {
            List<HighlightItem> remaining = highlightItemRepository.findByIdHighlightIdOrderByCreatedAtDesc(highlightId);
            highlight.setCoverStoryId(remaining.isEmpty() ? null : remaining.get(0).getId().getStoryId());
            highlightRepository.save(highlight);
        }
    }

    @Transactional
    public void deleteHighlight(UUID highlightId, UUID userId) {
        Highlight highlight = highlightRepository.findById(highlightId)
                .orElseThrow(() -> new ContentNotFoundException("Highlight not found"));

        if (!highlight.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not allowed to delete this highlight");
        }

        highlightRepository.delete(highlight);
    }

    private String resolveCoverMediaUrl(UUID coverStoryId) {
        if (coverStoryId == null) return null;
        return storyRepository.findById(coverStoryId)
                .map(Story::getMediaUrl)
                .orElse(null);
    }
}
