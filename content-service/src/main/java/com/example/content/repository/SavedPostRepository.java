package com.example.content.repository;

import com.example.content.entity.SavedPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPost.SavedPostId> {

    boolean existsByIdUserIdAndIdPostId(UUID userId, UUID postId);

    void deleteByIdUserIdAndIdPostId(UUID userId, UUID postId);

    Page<SavedPost> findByIdUserIdOrderBySavedAtDesc(UUID userId, Pageable pageable);
}
