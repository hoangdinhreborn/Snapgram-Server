package com.example.content.service;

import com.example.content.dto.HashtagResponse;
import com.example.content.dto.PostResponse;
import com.example.content.entity.Hashtag;
import com.example.content.entity.PostStatus;
import com.example.content.entity.Visibility;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.HashtagRepository;
import com.example.content.repository.PostHashtagRepository;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class HashtagService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostRepository postRepository;

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

        Page<java.util.UUID> postIds = postHashtagRepository.findPostIdsByHashtagId(hashtag.getId(), pageable);

        List<PostResponse> posts = postIds.getContent().stream()
                .map(id -> postRepository.findByIdAndStatusNot(id, PostStatus.DELETED))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                .map(p -> PostResponse.builder()
                        .id(p.getId().toString())
                        .authorId(p.getAuthorId().toString())
                        .contentType(p.getContentType().name())
                        .caption(p.getCaption())
                        .mediaId(p.getMediaId() != null ? p.getMediaId().toString() : null)
                        .mediaUrl(p.getMediaUrl())
                        .likeCount(p.getLikeCount())
                        .commentCount(p.getCommentCount())
                        .viewCount(p.getViewCount())
                        .status(p.getStatus().name())
                        .visibility(p.getVisibility().name())
                        .createdAt(p.getCreatedAt())
                        .updatedAt(p.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return new PageImpl<>(posts, pageable, postIds.getTotalElements());
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
