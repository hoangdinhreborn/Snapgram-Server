package com.example.content.repository;

import com.example.content.entity.PostHashtag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostHashtagRepository extends JpaRepository<PostHashtag, PostHashtag.PostHashtagId> {

    List<PostHashtag> findByIdPostId(UUID postId);

    void deleteByIdPostId(UUID postId);

    @Query("""
            SELECT ph.id.postId FROM PostHashtag ph
            WHERE ph.id.hashtagId = :hashtagId
            """)
    Page<UUID> findPostIdsByHashtagId(@Param("hashtagId") UUID hashtagId, Pageable pageable);
}
