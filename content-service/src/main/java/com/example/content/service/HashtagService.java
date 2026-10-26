package com.example.content.service;

import com.example.content.dto.HashtagResponse;
import com.example.content.dto.PostMediaResponse;
import com.example.content.dto.PostResponse;
import com.example.content.entity.Hashtag;
import com.example.content.entity.Post;
import com.example.content.entity.PostMedia;
import com.example.content.entity.PostStatus;
import com.example.content.entity.Visibility;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.HashtagRepository;
import com.example.content.repository.PostHashtagRepository;
import com.example.content.repository.PostMediaRepository;
import com.example.content.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HashtagService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;

    @Cacheable(value = "hashtag-search", key = "#q")
    @Transactional(readOnly = true)
    public List<HashtagResponse> searchHashtags(String q) {
        if (q == null || q.isBlank()) return Collections.emptyList();
        String prefix = q.toLowerCase().replaceAll("[^a-z0-9_]", "");
        return hashtagRepository.findByTagStartingWithIgnoreCaseOrderByPostCountDesc(prefix)
                .stream()
                .limit(20)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByHashtag(String tag, Pageable pageable) {
        Hashtag hashtag = hashtagRepository.findByTagIgnoreCase(tag)
                .orElseThrow(() -> new ContentNotFoundException("Hashtag not found: " + tag));

        Page<UUID> postIds = postHashtagRepository.findPostIdsByHashtagId(hashtag.getId(), pageable);

        List<Post> postEntities = postIds.getContent().stream()
                .map(id -> postRepository.findByIdAndStatusNot(id, PostStatus.DELETED))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                .toList();

        List<UUID> validPostIds = postEntities.stream().map(Post::getId).toList();

        Map<UUID, List<PostMediaResponse>> mediaByPostId = validPostIds.isEmpty() ? Collections.emptyMap() :
                postMediaRepository.findByPostIdInOrderBySortOrderAsc(validPostIds).stream()
                        .collect(Collectors.groupingBy(
                                pm -> pm.getPost().getId(),
                                Collectors.mapping(this::toMediaResponse, Collectors.toList())
                        ));

        List<PostResponse> posts = postEntities.stream()
                .map(p -> {
                    List<PostMediaResponse> mediaItems = mediaByPostId.get(p.getId());
                    String mediaUrl = p.getMediaUrl();
                    if (mediaUrl == null && mediaItems != null && !mediaItems.isEmpty()) {
                        mediaUrl = mediaItems.get(0).getMediaUrl();
                    }

                    return PostResponse.builder()
                            .id(p.getId().toString())
                            .authorId(p.getAuthorId().toString())
                            .contentType(p.getContentType().name())
                            .caption(p.getCaption())
                            .mediaId(p.getMediaId() != null ? p.getMediaId().toString() : null)
                            .mediaUrl(mediaUrl)
                            .mediaItems(mediaItems)
                            .likeCount(p.getLikeCount())
                            .commentCount(p.getCommentCount())
                            .viewCount(p.getViewCount())
                            .status(p.getStatus().name())
                            .visibility(p.getVisibility().name())
                            .createdAt(p.getCreatedAt())
                            .updatedAt(p.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(posts, pageable, postIds.getTotalElements());
    }

    private PostMediaResponse toMediaResponse(PostMedia pm) {
        return PostMediaResponse.builder()
                .id(pm.getId().toString())
                .mediaId(pm.getMediaId() != null ? pm.getMediaId().toString() : null)
                .mediaUrl(pm.getMediaUrl())
                .thumbnailUrl(pm.getThumbnailUrl())
                .mediaType(pm.getMediaType().name())
                .sortOrder(pm.getSortOrder())
                .width(pm.getWidth())
                .height(pm.getHeight())
                .durationSec(pm.getDurationSec())
                .createdAt(pm.getCreatedAt())
                .build();
    }

    private HashtagResponse toResponse(Hashtag h) {
        return HashtagResponse.builder()
                .id(h.getId().toString())
                .tag(h.getTag())
                .postCount(h.getPostCount())
                .lastUsedAt(h.getLastUsedAt())
                .build();
    }
}
