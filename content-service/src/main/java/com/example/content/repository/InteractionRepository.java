package com.example.content.repository;

import com.example.content.entity.Interaction;
import com.example.content.entity.InteractionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    Optional<Interaction> findByUserIdAndPostIdAndType(UUID userId, UUID postId, InteractionType type);

    boolean existsByUserIdAndPostIdAndType(UUID userId, UUID postId, InteractionType type);

    void deleteByUserIdAndPostIdAndType(UUID userId, UUID postId, InteractionType type);
}
