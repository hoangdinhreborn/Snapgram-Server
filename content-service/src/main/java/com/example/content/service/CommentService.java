package com.example.content.service;

import com.example.content.dto.CommentResponse;
import com.example.content.dto.CreateCommentRequest;
import com.example.content.entity.Comment;
import com.example.content.entity.CommentLike;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;

    @Transactional
    public CommentResponse createComment(UUID postId, UUID authorId, CreateCommentRequest request) {
        Post post = postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));

        // Validate parent comment belongs to same post
        if (request.getParentCommentId() != null) {
            commentRepository.findByIdAndPost_Id(request.getParentCommentId(), postId)
                    .orElseThrow(() -> new ContentNotFoundException("Parent comment not found in this post"));
        }

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthorId(authorId);
        comment.setContent(request.getContent());
        comment.setParentCommentId(request.getParentCommentId());
        Comment saved = commentRepository.save(comment);

        postRepository.incrementCommentCount(postId);

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getRootComments(UUID postId, UUID requesterId, Pageable pageable) {
        postRepository.findByIdAndStatusNot(postId, PostStatus.DELETED)
                .orElseThrow(() -> new ContentNotFoundException("Post not found"));
        return commentRepository.findRootCommentsByPostId(postId, pageable)
                .map(c -> toResponse(c, commentLikeRepository.existsByIdCommentIdAndIdUserId(c.getId(), requesterId)));
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getReplies(UUID postId, UUID parentCommentId, UUID requesterId, Pageable pageable) {
        return commentRepository.findReplies(postId, parentCommentId, pageable)
                .map(c -> toResponse(c, commentLikeRepository.existsByIdCommentIdAndIdUserId(c.getId(), requesterId)));
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

    private CommentResponse toResponse(Comment c, boolean likedByMe) {
        return CommentResponse.builder()
                .id(c.getId().toString())
                .postId(c.getPost().getId().toString())
                .authorId(c.getAuthorId().toString())
                .content(c.getContent())
                .parentCommentId(c.getParentCommentId() != null ? c.getParentCommentId().toString() : null)
                .likeCount(c.getLikeCount())
                .likedByMe(likedByMe)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
