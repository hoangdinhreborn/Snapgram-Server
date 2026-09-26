package com.example.content.repository;

import com.example.content.entity.HighlightItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HighlightItemRepository extends JpaRepository<HighlightItem, HighlightItem.HighlightItemId> {

    List<HighlightItem> findByIdHighlightIdOrderByCreatedAtDesc(UUID highlightId);

    void deleteByIdHighlightIdAndIdStoryId(UUID highlightId, UUID storyId);
}
