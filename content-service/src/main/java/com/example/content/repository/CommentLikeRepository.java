package com.example.content.repository;

import com.example.content.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLike, CommentLike.CommentLikeId> {

    Optional<CommentLike> findByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    boolean existsByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    void deleteByIdCommentIdAndIdUserId(UUID commentId, UUID userId);
}
