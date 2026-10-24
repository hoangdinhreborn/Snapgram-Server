package com.example.content.service;

import com.example.content.dto.CommentResponse;
import com.example.content.dto.CreateCommentRequest;
import com.example.content.entity.Comment;
import com.example.content.entity.CommentLike;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.event.ContentInteractionEvent;
import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.exception.DuplicateResourceException;
import com.example.content.repository.CommentLikeRepository;
import com.example.content.repository.CommentRepository;
import com.example.content.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {

    private static final String TOPIC_INTERACTION = "content.interaction";

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final com.example.content.client.AuthServiceClient authServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public CommentResponse createComment(UUID postId, UUID authorId, CreateCommentRequest request) {
        Post post = postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        // Validate parent comment belongs to same post and resolve to root parent if replying to a child (Instagram style - max depth 1)
        UUID resolvedParentId = null;
        UUID repliedToAuthorId = null;
        if (request.getParentCommentId() != null) {
            Comment targetParent = commentRepository.findByIdAndPost_Id(request.getParentCommentId(), postId)
                    .orElseThrow(() -> new ContentNotFoundException("Parent comment not found in this post"));
            repliedToAuthorId = targetParent.getAuthorId();
            resolvedParentId = targetParent.getParentCommentId() != null
                    ? targetParent.getParentCommentId()
                    : targetParent.getId();
        }

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthorId(authorId);
        comment.setContent(request.getContent());
        comment.setParentCommentId(resolvedParentId);
        Comment saved = commentRepository.save(comment);

        postRepository.incrementCommentCount(postId);

        publishCommentEvent(saved, post, authorId);
        publishReplyEvent(saved, post, repliedToAuthorId, authorId);

        com.example.content.client.AuthServiceClient.UserSummary author = authServiceClient.getUserSummary(authorId);
        return toResponse(saved, false, author);
    }

    /** Publish COMMENT interaction event so notification-service can notify the post author. */
    private void publishCommentEvent(Comment comment, Post post, UUID authorId) {
        if (post.getAuthorId() == null || post.getAuthorId().equals(authorId)) {
            return; // Do not notify yourself
        }
        try {
            kafkaTemplate.send(TOPIC_INTERACTION, post.getId().toString(), ContentInteractionEvent.builder()
                    .eventId(comment.getId().toString())
                    .userId(authorId.toString())
                    .targetUserId(post.getAuthorId().toString())
                    .postId(post.getId().toString())
                    .type("COMMENT")
                    .createdAt(Instant.now())
                    .build())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish comment interaction event: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to dispatch comment interaction event: {}", e.getMessage());
        }
    }

    /**
     * Publish a REPLY interaction event so the author of the comment being replied to
     * also gets notified (previously only the post author was notified).
     */
    private void publishReplyEvent(Comment comment, Post post, UUID repliedToAuthorId, UUID authorId) {
        if (repliedToAuthorId == null) {
            return; // Not a reply
        }
        if (repliedToAuthorId.equals(authorId)) {
            return; // Replying to yourself
        }
        if (post.getAuthorId() != null && repliedToAuthorId.equals(post.getAuthorId())) {
            return; // Post author already notified via the COMMENT event
        }
        try {
            kafkaTemplate.send(TOPIC_INTERACTION, post.getId().toString(), ContentInteractionEvent.builder()
                    .eventId(replyEventId(comment.getId()))
                    .userId(authorId.toString())
                    .targetUserId(repliedToAuthorId.toString())
                    .postId(post.getId().toString())
                    .type("REPLY")
                    .createdAt(Instant.now())
                    .build())
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish reply interaction event: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Failed to dispatch reply interaction event: {}", e.getMessage());
        }
    }

    /** Stable, distinct-from-commentId event id so reply notifications stay idempotent. */
    private String replyEventId(UUID commentId) {
        return UUID.nameUUIDFromBytes((commentId + ":reply").getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getRootComments(UUID postId, UUID requesterId, Pageable pageable) {
        postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));
        Page<Comment> comments = commentRepository.findRootCommentsByPostId(postId, pageable);
        List<UUID> authorIds = comments.getContent().stream().map(Comment::getAuthorId).distinct().toList();
        Map<UUID, com.example.content.client.AuthServiceClient.UserSummary> authorMap =
                authServiceClient.getUsersSummaryMap(authorIds);

        // Số trả lời của từng comment gốc — để client chỉ hiện nút "Xem trả lời" khi thực sự có
        List<UUID> rootIds = comments.getContent().stream().map(Comment::getId).toList();
        Map<UUID, Long> replyCounts = new java.util.HashMap<>();
        if (!rootIds.isEmpty()) {
            for (Object[] row : commentRepository.countRepliesByParentIds(postId, rootIds)) {
                replyCounts.put((UUID) row[0], (Long) row[1]);
            }
        }

        return comments.map(c -> toResponse(
                c,
                commentLikeRepository.existsByIdCommentIdAndIdUserId(c.getId(), requesterId),
                authorMap.get(c.getAuthorId()),
                replyCounts.getOrDefault(c.getId(), 0L)
        ));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getReplies(UUID postId, UUID parentCommentId, UUID requesterId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findReplies(postId, parentCommentId, pageable);
        List<UUID> authorIds = comments.getContent().stream().map(Comment::getAuthorId).distinct().toList();
        Map<UUID, com.example.content.client.AuthServiceClient.UserSummary> authorMap =
                authServiceClient.getUsersSummaryMap(authorIds);
        return comments.map(c -> toResponse(
                c,
                commentLikeRepository.existsByIdCommentIdAndIdUserId(c.getId(), requesterId),
                authorMap.get(c.getAuthorId())
        ));
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID requesterId) {
        Comment comment = commentRepository.findByIdAndPost_Id(commentId, postId)
                .orElseThrow(() -> new ContentNotFoundException("Comment not found"));

        if (!comment.getAuthorId().equals(requesterId)) {
            // Also allow post author to delete
            Post post = comment.getPost();
            if (!post.getAuthorId().equals(requesterId)) {
                throw new AccessDeniedException("You cannot delete this comment");
            }
        }

        commentRepository.delete(comment);
        postRepository.decrementCommentCount(postId);
    }

    @Transactional
    public void likeComment(UUID commentId, UUID userId) {
        commentRepository.findById(commentId)
                .orElseThrow(() -> new ContentNotFoundException("Comment not found"));

        if (commentLikeRepository.existsByIdCommentIdAndIdUserId(commentId, userId)) {
            throw new DuplicateResourceException("Already liked");
        }

        CommentLike.CommentLikeId likeId = new CommentLike.CommentLikeId();
        likeId.setCommentId(commentId);
        likeId.setUserId(userId);
        CommentLike like = new CommentLike();
        like.setId(likeId);
        commentLikeRepository.save(like);
        commentRepository.incrementLikeCount(commentId);
    }

    @Transactional
    public void unlikeComment(UUID commentId, UUID userId) {
        if (!commentLikeRepository.existsByIdCommentIdAndIdUserId(commentId, userId)) {
            return; // Idempotent
        }
        commentLikeRepository.deleteByIdCommentIdAndIdUserId(commentId, userId);
        commentRepository.decrementLikeCount(commentId);
    }

    private CommentResponse toResponse(Comment c, boolean likedByMe,
                                       com.example.content.client.AuthServiceClient.UserSummary author) {
        return toResponse(c, likedByMe, author, null);
    }

    private CommentResponse toResponse(Comment c, boolean likedByMe,
                                       com.example.content.client.AuthServiceClient.UserSummary author,
                                       Long replyCount) {
        CommentResponse.CommentResponseBuilder builder = CommentResponse.builder()
                .id(c.getId().toString())
                .postId(c.getPost().getId().toString())
                .authorId(c.getAuthorId().toString())
                .content(c.getContent())
                .parentCommentId(c.getParentCommentId() != null ? c.getParentCommentId().toString() : null)
                .likeCount(c.getLikeCount())
                .likedByMe(likedByMe)
                .replyCount(replyCount)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt());

        if (author != null) {
            builder.authorUsername(author.getUsername())
                   .authorDisplayName(author.getDisplayName())
                   .authorAvatarUrl(author.getAvatarUrl());
        }

        return builder.build();
    }
}
