package com.example.content.repository;

import com.example.content.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Root comments only (no parent) */
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parentCommentId IS NULL ORDER BY c.createdAt ASC")
    Page<Comment> findRootCommentsByPostId(@Param("postId") UUID postId, Pageable pageable);

    /** Replies to a specific comment */
    @Query("SELECT c FROM Comment c WHERE c.post.id = :postId AND c.parentCommentId = :parentId ORDER BY c.createdAt ASC")
    Page<Comment> findReplies(@Param("postId") UUID postId, @Param("parentId") UUID parentId, Pageable pageable);

    Optional<Comment> findByIdAndPost_Id(UUID id, UUID postId);

    @Modifying
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Comment c SET c.likeCount = GREATEST(c.likeCount - 1, 0) WHERE c.id = :id")
    void decrementLikeCount(@Param("id") UUID id);
}
