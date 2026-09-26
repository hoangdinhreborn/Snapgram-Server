package com.example.content.repository;

import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndStatusNot(UUID id, PostStatus status);

    Page<Post> findByAuthorIdAndStatusNotOrderByCreatedAtDesc(UUID authorId, PostStatus status, Pageable pageable);

    Page<Post> findByStatusAndVisibilityOrderByCreatedAtDesc(PostStatus status, Visibility visibility, Pageable pageable);

    /** Feed: posts from a list of following user IDs + own, not deleted, with visibility filter */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId IN :authorIds
              AND p.status = 'PUBLISHED'
              AND p.visibility IN :visibilities
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findFeedPosts(@Param("authorIds") List<UUID> authorIds,
                             @Param("visibilities") List<Visibility> visibilities,
                             Pageable pageable);

    @Query("""
            SELECT p FROM Post p
            WHERE p.authorId = :authorId
              AND p.status = 'PUBLISHED'
              AND p.visibility IN :visibilities
            ORDER BY p.createdAt DESC
            """)
    Page<Post> findPublicPostsByAuthor(@Param("authorId") UUID authorId,
                                       @Param("visibilities") List<Visibility> visibilities,
                                       Pageable pageable);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = GREATEST(p.likeCount - 1, 0) WHERE p.id = :id")
    void decrementLikeCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    void incrementCommentCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = GREATEST(p.commentCount - 1, 0) WHERE p.id = :id")
    void decrementCommentCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id);
}
