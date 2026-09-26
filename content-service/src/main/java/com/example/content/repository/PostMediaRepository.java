package com.example.content.repository;

import com.example.content.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostMediaRepository extends JpaRepository<PostMedia, UUID> {

    @Query("SELECT pm FROM PostMedia pm WHERE pm.post.id = :postId ORDER BY pm.sortOrder ASC")
    List<PostMedia> findByPostIdOrderBySortOrderAsc(@Param("postId") UUID postId);

    @Query("SELECT pm FROM PostMedia pm WHERE pm.post.id IN :postIds ORDER BY pm.sortOrder ASC")
    List<PostMedia> findByPostIdInOrderBySortOrderAsc(@Param("postIds") List<UUID> postIds);

    @Query("DELETE FROM PostMedia pm WHERE pm.post.id = :postId")
    void deleteByPostId(@Param("postId") UUID postId);
}
