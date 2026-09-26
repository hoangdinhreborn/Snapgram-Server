package com.example.content.repository;

import com.example.content.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    List<Collection> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Collection> findByIdAndUserId(UUID id, UUID userId);
}
