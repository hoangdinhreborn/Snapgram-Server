package com.example.content.repository;

import com.example.content.entity.SavedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPost.SavedPostId> {

    boolean existsByIdUserIdAndIdPostId(UUID userId, UUID postId);

    void deleteByIdUserIdAndIdPostId(UUID userId, UUID postId);

    Page<SavedPost> findByIdUserIdOrderBySavedAtDesc(UUID userId, Pageable pageable);

    /** Các post đã lưu trong một tập post — dùng để set cờ savedByMe cho cả trang. */
    @Query("SELECT s.id.postId FROM SavedPost s WHERE s.id.userId = :userId AND s.id.postId IN :postIds")
    Set<UUID> findSavedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
