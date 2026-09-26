package com.example.content.repository;

import com.example.content.entity.Hashtag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HashtagRepository extends JpaRepository<Hashtag, UUID> {

    Optional<Hashtag> findByTagIgnoreCase(String tag);

    List<Hashtag> findByTagStartingWithIgnoreCaseOrderByPostCountDesc(String prefix);

    @Modifying
    @Query("UPDATE Hashtag h SET h.postCount = h.postCount + 1, h.lastUsedAt = CURRENT_TIMESTAMP WHERE h.id = :id")
    void incrementPostCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Hashtag h SET h.postCount = GREATEST(h.postCount - 1, 0) WHERE h.id = :id")
    void decrementPostCount(@Param("id") UUID id);
}
