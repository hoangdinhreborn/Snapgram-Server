package com.example.content.service;

import com.example.content.dto.*;
import com.example.content.entity.*;
import com.example.content.event.*;
import com.example.content.exception.*;
import com.example.content.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private static final Pattern HASHTAG_REGEX = Pattern.compile("#([\\p{Alnum}_]+)");

    private static final String TOPIC_POST_CREATED = "content.post-created";
    private static final String TOPIC_POST_UPDATED = "content.post-updated";
    private static final String TOPIC_MENTION = "mention.events";
    private static final String CACHE_POST = "posts";
    private static final int MAX_MEDIA_ITEMS = 10;

    private final PostRepository postRepository;
    private final PostMediaRepository postMediaRepository;
    private final FollowRepository followRepository;
    private final MuteRepository muteRepository;
    private final CloseFriendRepository closeFriendRepository;
    private final UserPrivacyViewRepository userPrivacyViewRepository;
    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final PostMentionRepository postMentionRepository;
    private final InteractionRepository interactionRepository;
    private final SavedPostRepository savedPostRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.example.content.client.RecommenderClient recommenderClient;
    private final com.example.content.client.AuthServiceClient authServiceClient;

    @Transactional
    public PostResponse createPost(UUID authorId, CreatePostRequest request) {
        List<PostMediaRequest> mediaReqList = request.getMediaList();
        if (mediaReqList != null && mediaReqList.size() > MAX_MEDIA_ITEMS) {
            throw new IllegalArgumentException("Maximum " + MAX_MEDIA_ITEMS + " media items allowed per post");
        }

        // 1. Determine ContentType & Cover Media
        PostType type;
        String coverMediaUrl = null;
        UUID coverMediaId = null;

        if (mediaReqList != null && !mediaReqList.isEmpty()) {
            if (request.getContentType() != null && !request.getContentType().isBlank()) {
                type = parsePostType(request.getContentType());
            } else if (mediaReqList.size() > 1) {
                type = PostType.CAROUSEL;
            } else {
                PostMediaRequest first = mediaReqList.get(0);
                type = "VIDEO".equalsIgnoreCase(first.getMediaType()) ? PostType.VIDEO : PostType.IMAGE;
            }
            PostMediaRequest first = mediaReqList.get(0);
            coverMediaUrl = first.getMediaUrl();
            coverMediaId = first.getMediaId();
        } else if (request.getMediaUrl() != null && !request.getMediaUrl().isBlank()) {
            type = request.getContentType() != null ? parsePostType(request.getContentType()) : PostType.IMAGE;
            coverMediaUrl = request.getMediaUrl();
            coverMediaId = request.getMediaId();
        } else {
            type = request.getContentType() != null ? parsePostType(request.getContentType()) : PostType.TEXT;
        }

        // 2. Determine Visibility
        Visibility visibility = Visibility.parse(request.getVisibility());

        // 3. Save Post entity
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setContentType(type);
        post.setCaption(request.getCaption());
        post.setMediaId(coverMediaId);
        post.setMediaUrl(coverMediaUrl);
        post.setTags(request.getTags());
        post.setVisibility(visibility);
        Post saved = postRepository.save(post);

        // 4. Save PostMedia items
        List<PostMediaResponse> mediaResponses = new ArrayList<>();
        List<String> mediaUrls = new ArrayList<>();

        if (mediaReqList != null && !mediaReqList.isEmpty()) {
            for (int i = 0; i < mediaReqList.size(); i++) {
                PostMediaRequest mr = mediaReqList.get(i);
                PostMedia pm = new PostMedia();
                pm.setPost(saved);
                pm.setMediaId(mr.getMediaId());
                pm.setMediaUrl(mr.getMediaUrl());
                pm.setThumbnailUrl(mr.getThumbnailUrl());
                PostMediaType pmType = "VIDEO".equalsIgnoreCase(mr.getMediaType()) ? PostMediaType.VIDEO : PostMediaType.IMAGE;
                pm.setMediaType(pmType);
                pm.setSortOrder(mr.getSortOrder() > 0 ? mr.getSortOrder() : i);
                pm.setWidth(mr.getWidth());
                pm.setHeight(mr.getHeight());
                pm.setDurationSec(mr.getDurationSec());

                PostMedia savedMedia = postMediaRepository.save(pm);
                mediaResponses.add(toMediaResponse(savedMedia));
                mediaUrls.add(savedMedia.getMediaUrl());
            }
        } else if (coverMediaUrl != null) {
            PostMedia pm = new PostMedia();
            pm.setPost(saved);
            pm.setMediaId(coverMediaId);
            pm.setMediaUrl(coverMediaUrl);
            pm.setMediaType(type == PostType.VIDEO ? PostMediaType.VIDEO : PostMediaType.IMAGE);
            pm.setSortOrder(0);
            PostMedia savedMedia = postMediaRepository.save(pm);
            mediaResponses.add(toMediaResponse(savedMedia));
            mediaUrls.add(coverMediaUrl);
        }

        // 5. Handle hashtags (extract from caption + explicit list)
        Set<String> tagSet = extractHashtagsFromCaption(request.getCaption());
        if (request.getHashtags() != null) {
            for (String tag : request.getHashtags()) {
                String normalized = tag.toLowerCase().replaceAll("[^a-z0-9_]", "");
                if (!normalized.isBlank()) tagSet.add(normalized);
            }
        }

        List<String> hashtags = new ArrayList<>();
        for (String normalized : tagSet) {
            Hashtag hashtag = hashtagRepository.findByTagIgnoreCase(normalized)
                    .orElseGet(() -> {
                        Hashtag h = new Hashtag();
                        h.setTag(normalized);
                        return hashtagRepository.save(h);
                    });
            hashtagRepository.incrementPostCount(hashtag.getId());
            PostHashtag ph = new PostHashtag();
            PostHashtag.PostHashtagId phId = new PostHashtag.PostHashtagId();
            phId.setPostId(saved.getId());
            phId.setHashtagId(hashtag.getId());
            ph.setId(phId);
            postHashtagRepository.save(ph);
            hashtags.add(normalized);
        }

        // 6. Handle mentions
        List<String> mentionedIds = new ArrayList<>();
        if (request.getMentionedUserIds() != null) {
            for (UUID mentionedId : request.getMentionedUserIds()) {
                PostMention.PostMentionId pmId = new PostMention.PostMentionId();
                pmId.setPostId(saved.getId());
                pmId.setMentionedUserId(mentionedId);
                PostMention pm = new PostMention();
                pm.setId(pmId);
                postMentionRepository.save(pm);
                mentionedIds.add(mentionedId.toString());

                // Publish mention event
                try {
                    kafkaTemplate.send(TOPIC_MENTION, mentionedId.toString(), MentionEvent.builder()
                            .eventId(EventIds.stableFor(TOPIC_MENTION, saved.getId().toString(), mentionedId.toString()))
                            .mentionedUserId(mentionedId.toString())
                            .postId(saved.getId().toString())
                            .authorId(authorId.toString())
                            .createdAt(saved.getCreatedAt())
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to send mention event: {}", e.getMessage());
                }
            }
        }

        // 7. Publish post-created event
        try {
            List<String> followerIds = visibility == Visibility.PUBLIC || visibility == Visibility.FOLLOWERS
                ? followRepository.findAcceptedFollowerIds(authorId).stream()
                    .map(UUID::toString)
                    .toList()
                : List.of();

            kafkaTemplate.send(TOPIC_POST_CREATED, saved.getId().toString(), PostCreatedEvent.builder()
                    .eventId(saved.getId().toString())
                    .postId(saved.getId().toString())
                    .authorId(authorId.toString())
                    .contentType(type.name())
                    .visibility(visibility.name())
                    .mediaId(coverMediaId != null ? coverMediaId.toString() : null)
                    .mediaUrl(coverMediaUrl)
                    .mediaUrls(mediaUrls)
                    .hashtags(hashtags)
                    .mentionedUserIds(mentionedIds)
                    .followerIds(followerIds)
                    .createdAt(saved.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish post-created event: {}", e.getMessage());
        }

        log.info("Post created: id={}, authorId={}, type={}, mediaCount={}",
                saved.getId(), authorId, type, mediaResponses.size());
        com.example.content.client.AuthServiceClient.UserSummary author = authServiceClient.getUserSummary(authorId);
        return toResponse(saved, hashtags, mentionedIds, mediaResponses, false, author);
    }

    @Cacheable(value = CACHE_POST, key = "#id")
    @Transactional(readOnly = true)
    public PostResponse getPost(UUID id, UUID requesterId) {
        Post post = postRepository.findByIdAndStatusNot(id, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found: " + id));

        checkVisibility(post, requesterId);

        List<String> hashtags = getPostHashtags(id);
        List<String> mentions = getPostMentions(id);
        List<PostMediaResponse> mediaItems = postMediaRepository.findByPostIdOrderBySortOrderAsc(id).stream()
                .map(this::toMediaResponse)
                .toList();

        boolean likedByMe = requesterId != null &&
                interactionRepository.existsByUserIdAndPostIdAndType(requesterId, id, InteractionType.LIKE);
        com.example.content.client.AuthServiceClient.UserSummary author = authServiceClient.getUserSummary(post.getAuthorId());
        PostResponse response = toResponse(post, hashtags, mentions, mediaItems, likedByMe, author);
        if (requesterId != null) {
            response.setSavedByMe(savedPostRepository.existsByIdUserIdAndIdPostId(requesterId, id));
        }
        return response;
    }

    @CacheEvict(value = CACHE_POST, key = "#postId")
    @Transactional
    public PostResponse updatePost(UUID postId, UUID authorId, UpdatePostRequest request) {
        Post post = postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        if (!post.getAuthorId().equals(authorId)) {
            throw new AccessDeniedException("You cannot edit this post");
        }

        if (request.getCaption() != null) post.setCaption(request.getCaption());
        if (request.getTags() != null) post.setTags(request.getTags());
        if (request.getVisibility() != null) {
            post.setVisibility(Visibility.parse(request.getVisibility()));
        }
        post.setEditedAt(Instant.now());
        post.setEditCount(post.getEditCount() + 1);
        Post saved = postRepository.save(post);

        try {
            kafkaTemplate.send(TOPIC_POST_UPDATED, saved.getId().toString(), PostUpdatedEvent.builder()
                    .postId(saved.getId().toString())
                    .authorId(authorId.toString())
                    .caption(saved.getCaption())
                    .visibility(saved.getVisibility().name())
                    .updatedAt(saved.getUpdatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish post-updated event: {}", e.getMessage());
        }

        List<PostMediaResponse> mediaItems = postMediaRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(this::toMediaResponse)
                .toList();

        com.example.content.client.AuthServiceClient.UserSummary author = authServiceClient.getUserSummary(authorId);
        return toResponse(saved, getPostHashtags(postId), getPostMentions(postId), mediaItems, false, author);
    }

    @CacheEvict(value = CACHE_POST, key = "#postId")
    @Transactional
    public void deletePost(UUID postId, UUID authorId) {
        Post post = postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        if (!post.getAuthorId().equals(authorId)) {
            throw new AccessDeniedException("You cannot delete this post");
        }

        post.setStatus(PostStatus.DELETED);
        postRepository.save(post);

        // Decrement hashtag counts and remove relation
        List<PostHashtag> postHashtags = postHashtagRepository.findByIdPostId(postId);
        for (PostHashtag ph : postHashtags) {
            hashtagRepository.decrementPostCount(ph.getId().getHashtagId());
        }
        postHashtagRepository.deleteByIdPostId(postId);

        log.info("Post soft-deleted and hashtags cleaned up: id={}", postId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getMyPosts(UUID authorId, Pageable pageable) {
        Page<Post> postsPage = postRepository.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(
                authorId, PostStatus.DELETED, pageable);
        return enrichPageWithMedia(postsPage, authorId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getUserPosts(UUID targetUserId, UUID requesterId, Pageable pageable) {
        List<Visibility> visibilities = resolveVisibilities(targetUserId, requesterId);
        Page<Post> postsPage = postRepository.findPublicPostsByAuthor(targetUserId, PostStatus.DELETED, visibilities, pageable);
        return enrichPageWithMedia(postsPage, requesterId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(UUID userId, Pageable pageable) {
        // Try getting recommended order from recommender-service
        List<com.example.content.client.RecommenderClient.RecommendedItem> recs =
                recommenderClient.getPersonalFeedRecommendations(userId, pageable.getPageNumber(), pageable.getPageSize());

        if (recs != null && !recs.isEmpty()) {
            List<UUID> postIds = recs.stream().map(com.example.content.client.RecommenderClient.RecommendedItem::getPostId).toList();
            List<Post> posts = postRepository.findAllById(postIds);
            Map<UUID, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));

            Map<UUID, String> reasonMap = recs.stream()
                    .filter(r -> r.getRecommendationReason() != null)
                    .collect(Collectors.toMap(
                            com.example.content.client.RecommenderClient.RecommendedItem::getPostId,
                            com.example.content.client.RecommenderClient.RecommendedItem::getRecommendationReason,
                            (a, b) -> a
                    ));

            List<Post> orderedPosts = new ArrayList<>();
            for (UUID id : postIds) {
                Post p = postMap.get(id);
                if (p != null && p.getStatus() != PostStatus.DELETED) {
                    orderedPosts.add(p);
                }
            }

            if (!orderedPosts.isEmpty()) {
                Page<Post> pagedRecs = new org.springframework.data.domain.PageImpl<>(
                        orderedPosts, pageable, orderedPosts.size() >= pageable.getPageSize() ? (long) (pageable.getPageNumber() + 2) * pageable.getPageSize() : orderedPosts.size());
                return enrichPageWithMediaAndReason(pagedRecs, userId, reasonMap);
            }
        }

        // Fallback to following list in DB
        List<UUID> followingIds = followRepository.findAcceptedFollowingIds(userId);
        List<UUID> mutedIds = muteRepository.findMutedIds(userId);

        List<UUID> authorIds = new ArrayList<>();
        authorIds.add(userId);
        followingIds.stream()
                .filter(id -> !mutedIds.contains(id))
                .forEach(authorIds::add);

        Page<Post> postsPage;
        if (authorIds.size() == 1) {
            postsPage = postRepository.findByAuthorIdAndStatusNotOrderByCreatedAtDesc(userId, PostStatus.DELETED, pageable);
        } else {
            List<Visibility> visibilities = List.of(Visibility.PUBLIC, Visibility.FOLLOWERS);
            postsPage = postRepository.findFeedPosts(authorIds, PostStatus.PUBLISHED, visibilities, pageable);
        }

        // If following + own posts are empty (e.g. newly registered user who hasn't posted or following accounts haven't posted),
        // fallback to public posts so that the feed is never blank
        if (postsPage.isEmpty()) {
            postsPage = postRepository.findByStatusAndVisibilityOrderByCreatedAtDesc(PostStatus.PUBLISHED, Visibility.PUBLIC, pageable);
        }

        return enrichPageWithMedia(postsPage, userId);
    }

    /**
     * Danh sách Reels công khai có phân trang — cho tab Reels lazy-load.
     * Gồm cả REEL và VIDEO để không bỏ sót clip dọc/ngang.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> getReels(UUID requesterId, Pageable pageable) {
        Page<Post> reels = postRepository.findReels(
                List.of(PostType.REEL, PostType.VIDEO),
                PostStatus.PUBLISHED,
                Visibility.PUBLIC,
                pageable);
        return enrichPageWithMedia(reels, requesterId);
    }

    public Page<PostResponse> getExplorePosts(UUID requesterId, Pageable pageable) {
        // Try getting recommended order from recommender-service
        List<com.example.content.client.RecommenderClient.RecommendedItem> recs =
                recommenderClient.getExploreFeedRecommendations(requesterId, pageable.getPageNumber(), pageable.getPageSize());

        if (recs != null && !recs.isEmpty()) {
            List<UUID> postIds = recs.stream().map(com.example.content.client.RecommenderClient.RecommendedItem::getPostId).toList();
            List<Post> posts = postRepository.findAllById(postIds);
            Map<UUID, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, p -> p));

            Map<UUID, String> reasonMap = recs.stream()
                    .filter(r -> r.getRecommendationReason() != null)
                    .collect(Collectors.toMap(
                            com.example.content.client.RecommenderClient.RecommendedItem::getPostId,
                            com.example.content.client.RecommenderClient.RecommendedItem::getRecommendationReason,
                            (a, b) -> a
                    ));

            List<Post> orderedPosts = new ArrayList<>();
            for (UUID id : postIds) {
                Post p = postMap.get(id);
                if (p != null && p.getStatus() == PostStatus.PUBLISHED && p.getVisibility() == Visibility.PUBLIC) {
                    orderedPosts.add(p);
                }
            }

            if (!orderedPosts.isEmpty()) {
                Page<Post> pagedRecs = new org.springframework.data.domain.PageImpl<>(
                        orderedPosts, pageable, orderedPosts.size() >= pageable.getPageSize() ? (long) (pageable.getPageNumber() + 2) * pageable.getPageSize() : orderedPosts.size());
                return enrichPageWithMediaAndReason(pagedRecs, requesterId, reasonMap);
            }
        }

        Page<Post> postsPage = postRepository.findByStatusAndVisibilityOrderByCreatedAtDesc(
                PostStatus.PUBLISHED, Visibility.PUBLIC, pageable);
        return enrichPageWithMedia(postsPage, requesterId);
    }

    private Page<PostResponse> enrichPageWithMediaAndReason(Page<Post> page, UUID requesterId, Map<UUID, String> reasonMap) {
        List<UUID> postIds = page.getContent().stream().map(Post::getId).toList();
        List<UUID> authorIds = page.getContent().stream().map(Post::getAuthorId).distinct().toList();
        Map<UUID, com.example.content.client.AuthServiceClient.UserSummary> authorMap =
                authServiceClient.getUsersSummaryMap(authorIds);

        Map<UUID, List<PostMediaResponse>> mediaByPostId = postIds.isEmpty() ? Collections.emptyMap() :
                postMediaRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                        .collect(Collectors.groupingBy(
                                pm -> pm.getPost().getId(),
                                Collectors.mapping(this::toMediaResponse, Collectors.toList())
                        ));

        Set<UUID> likedPostIds = (requesterId != null && !postIds.isEmpty())
                ? interactionRepository.findPostIdsByUserIdAndTypeAndPostIdIn(requesterId, InteractionType.LIKE, postIds)
                : Collections.emptySet();

        Set<UUID> savedPostIds = (requesterId != null && !postIds.isEmpty())
                ? savedPostRepository.findSavedPostIds(requesterId, postIds)
                : Collections.emptySet();

        return page.map(p -> {
            PostResponse res = toResponse(
                    p,
                    getPostHashtags(p.getId()),
                    Collections.emptyList(),
                    mediaByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                    likedPostIds.contains(p.getId()),
                    authorMap.get(p.getAuthorId())
            );
            if (requesterId != null) {
                res.setSavedByMe(savedPostIds.contains(p.getId()));
            }
            if (reasonMap != null && reasonMap.containsKey(p.getId())) {
                res.setRecommendationReason(reasonMap.get(p.getId()));
            }
            return res;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Page<PostResponse> enrichPageWithMedia(Page<Post> page, UUID requesterId) {
        List<UUID> postIds = page.getContent().stream().map(Post::getId).toList();
        List<UUID> authorIds = page.getContent().stream().map(Post::getAuthorId).distinct().toList();
        Map<UUID, com.example.content.client.AuthServiceClient.UserSummary> authorMap =
                authServiceClient.getUsersSummaryMap(authorIds);

        Map<UUID, List<PostMediaResponse>> mediaByPostId = postIds.isEmpty() ? Collections.emptyMap() :
                postMediaRepository.findByPostIdInOrderBySortOrderAsc(postIds).stream()
                        .collect(Collectors.groupingBy(
                                pm -> pm.getPost().getId(),
                                Collectors.mapping(this::toMediaResponse, Collectors.toList())
                        ));

        Set<UUID> likedPostIds = (requesterId != null && !postIds.isEmpty())
                ? interactionRepository.findPostIdsByUserIdAndTypeAndPostIdIn(requesterId, InteractionType.LIKE, postIds)
                : Collections.emptySet();

        Set<UUID> savedPostIds = (requesterId != null && !postIds.isEmpty())
                ? savedPostRepository.findSavedPostIds(requesterId, postIds)
                : Collections.emptySet();

        return page.map(p -> {
            PostResponse res = toResponse(
                    p,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    mediaByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                    likedPostIds.contains(p.getId()),
                    authorMap.get(p.getAuthorId())
            );
            if (requesterId != null) {
                res.setSavedByMe(savedPostIds.contains(p.getId()));
            }
            return res;
        });
    }

    private PostType parsePostType(String typeStr) {
        try {
            return PostType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PostType.IMAGE;
        }
    }

    private void checkVisibility(Post post, UUID requesterId) {
        if (post.getAuthorId().equals(requesterId)) return;
        if (post.getVisibility() == Visibility.PRIVATE) {
            throw new AccessDeniedException("This post is private");
        }
        if (post.getVisibility() == Visibility.FOLLOWERS) {
            boolean isFollowing = followRepository.existsByFollowerIdAndFollowingIdAndStatus(
                    requesterId, post.getAuthorId(), FollowStatus.ACCEPTED);
            if (!isFollowing) throw new AccessDeniedException("This post is for followers only");
        }
        if (post.getVisibility() == Visibility.CLOSE_FRIENDS) {
            boolean isCloseFriend = closeFriendRepository.existsByIdOwnerIdAndIdFriendId(
                    post.getAuthorId(), requesterId);
            if (!isCloseFriend) throw new AccessDeniedException("This post is for close friends only");
        }
    }

    private List<Visibility> resolveVisibilities(UUID targetUserId, UUID requesterId) {
        if (requesterId != null && targetUserId.equals(requesterId)) {
            return List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.FOLLOWERS_ONLY, Visibility.CLOSE_FRIENDS, Visibility.PRIVATE);
        }
        if (requesterId != null) {
            boolean isFollowing = followRepository.existsByFollowerIdAndFollowingIdAndStatus(
                    requesterId, targetUserId, FollowStatus.ACCEPTED);
            if (isFollowing) {
                boolean isCloseFriend = closeFriendRepository.existsByIdOwnerIdAndIdFriendId(targetUserId, requesterId);
                return isCloseFriend
                        ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.FOLLOWERS_ONLY, Visibility.CLOSE_FRIENDS)
                        : List.of(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.FOLLOWERS_ONLY);
            }
        }
        return List.of(Visibility.PUBLIC);
    }

    private Set<String> extractHashtagsFromCaption(String caption) {
        if (caption == null || caption.isBlank()) return new HashSet<>();
        Set<String> tags = new HashSet<>();
        Matcher matcher = HASHTAG_REGEX.matcher(caption);
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase().replaceAll("[^a-z0-9_]", "");
            if (!tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private List<String> getPostHashtags(UUID postId) {
        List<PostHashtag> relations = postHashtagRepository.findByIdPostId(postId);
        if (relations.isEmpty()) return Collections.emptyList();

        List<UUID> hashtagIds = relations.stream()
                .map(ph -> ph.getId().getHashtagId())
                .toList();

        return hashtagRepository.findAllById(hashtagIds).stream()
                .map(Hashtag::getTag)
                .collect(Collectors.toList());
    }

    private List<String> getPostMentions(UUID postId) {
        return postMentionRepository.findByIdPostId(postId).stream()
                .map(pm -> pm.getId().getMentionedUserId().toString())
                .collect(Collectors.toList());
    }

    public PostMediaResponse toMediaResponse(PostMedia pm) {
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

    private PostResponse toResponse(Post p, List<String> hashtags, List<String> mentions,
                                    List<PostMediaResponse> mediaItems, boolean likedByMe,
                                    com.example.content.client.AuthServiceClient.UserSummary author) {
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
                .id(p.getId().toString())
                .authorId(p.getAuthorId().toString())
                .contentType(p.getContentType().name())
                .caption(p.getCaption())
                .mediaId(p.getMediaId() != null ? p.getMediaId().toString() : null)
                .mediaUrl(p.getMediaUrl())
                .mediaItems(mediaItems != null && !mediaItems.isEmpty() ? mediaItems : null)
                .tags(p.getTags())
                .likeCount(p.getLikeCount())
                .commentCount(p.getCommentCount())
                .viewCount(p.getViewCount())
                .status(p.getStatus().name())
                .visibility(p.getVisibility().name())
                .editCount(p.getEditCount())
                .editedAt(p.getEditedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .hashtags(hashtags.isEmpty() ? null : hashtags)
                .mentionedUserIds(mentions.isEmpty() ? null : mentions)
                .likedByMe(likedByMe);

        if (author != null) {
            builder.authorUsername(author.getUsername())
                   .authorDisplayName(author.getDisplayName())
                   .authorAvatarUrl(author.getAvatarUrl());
        }

        return builder.build();
    }
}
