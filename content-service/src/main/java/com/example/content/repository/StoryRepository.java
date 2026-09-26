package com.example.content.repository;

import com.example.content.entity.Story;
import com.example.content.entity.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    /** Active stories from a list of author IDs */
    @Query("""
            SELECT s FROM Story s
            WHERE s.authorId IN :authorIds
              AND s.expiresAt > :now
              AND s.visibility IN :visibilities
            ORDER BY s.createdAt DESC
            """)
    Page<Story> findActiveStoriesForFeed(@Param("authorIds") List<UUID> authorIds,
                                         @Param("now") Instant now,
                                         @Param("visibilities") List<Visibility> visibilities,
                                         Pageable pageable);

    List<Story> findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(UUID authorId, Instant now);
}
