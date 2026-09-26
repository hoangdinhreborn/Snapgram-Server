package com.example.content.repository;

import com.example.content.entity.StoryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, StoryView.StoryViewId> {

    boolean existsByIdStoryIdAndIdViewerId(UUID storyId, UUID viewerId);

    Page<StoryView> findByIdStoryIdOrderByViewedAtDesc(UUID storyId, Pageable pageable);
}
