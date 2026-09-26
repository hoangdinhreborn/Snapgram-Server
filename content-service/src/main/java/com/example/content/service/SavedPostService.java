package com.example.content.service;

import com.example.content.dto.CollectionResponse;
import com.example.content.dto.CreateCollectionRequest;
import com.example.content.dto.PostResponse;
import com.example.content.dto.SavePostRequest;
import com.example.content.entity.*;
import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.exception.DuplicateResourceException;
import com.example.content.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SavedPostService {

    private final SavedPostRepository savedPostRepository;
    private final CollectionRepository collectionRepository;
    private final PostRepository postRepository;

    @Transactional
    public void savePost(UUID userId, UUID postId, SavePostRequest request) {
        postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        if (savedPostRepository.existsByIdUserIdAndIdPostId(userId, postId)) {
            throw new DuplicateResourceException("Post already saved");
        }

        UUID collectionId = request != null ? request.getCollectionId() : null;
        if (collectionId != null) {
            collectionRepository.findByIdAndUserId(collectionId, userId)
                    .orElseThrow(() -> new ContentNotFoundException("Collection not found"));
        }

        SavedPost.SavedPostId id = new SavedPost.SavedPostId();
        id.setUserId(userId);
        id.setPostId(postId);
        SavedPost savedPost = new SavedPost();
        savedPost.setId(id);
        savedPost.setCollectionId(collectionId);
        savedPostRepository.save(savedPost);
    }

    @Transactional
    public void unsavePost(UUID userId, UUID postId) {
        if (!savedPostRepository.existsByIdUserIdAndIdPostId(userId, postId)) {
            return; // Idempotent
        }
        savedPostRepository.deleteByIdUserIdAndIdPostId(userId, postId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getSavedPosts(UUID userId, Pageable pageable) {
        Page<SavedPost> savedPosts = savedPostRepository.findByIdUserIdOrderBySavedAtDesc(userId, pageable);
        List<PostResponse> responses = savedPosts.getContent().stream()
                .map(sp -> postRepository.findById(sp.getId().getPostId()).orElse(null))
                .filter(p -> p != null && p.getStatus() != PostStatus.DELETED)
                .map(this::toPostResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, savedPosts.getTotalElements());
    }

    @Transactional
    public CollectionResponse createCollection(UUID userId, CreateCollectionRequest request) {
        Collection col = new Collection();
        col.setUserId(userId);
        col.setName(request.getName());
        col.setPrivate(request.isPrivate());
        Collection saved = collectionRepository.save(col);
        return toCollectionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CollectionResponse> getMyCollections(UUID userId) {
        return collectionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toCollectionResponse).toList();
    }

    @Transactional
    public void deleteCollection(UUID collectionId, UUID userId) {
        Collection col = collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new ContentNotFoundException("Collection not found"));
        collectionRepository.delete(col);
    }

    private PostResponse toPostResponse(Post p) {
        return PostResponse.builder()
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
                .build();
    }

    private CollectionResponse toCollectionResponse(Collection c) {
        return CollectionResponse.builder()
                .id(c.getId().toString())
                .userId(c.getUserId().toString())
                .name(c.getName())
                .isPrivate(c.isPrivate())
                .coverPostId(c.getCoverPostId() != null ? c.getCoverPostId().toString() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
