package com.example.content.repository;

import com.example.content.entity.Highlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HighlightRepository extends JpaRepository<Highlight, UUID> {

    List<Highlight> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Highlight> findByIdAndUserId(UUID id, UUID userId);
}
