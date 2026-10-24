package com.example.content.repository;

import com.example.content.entity.Interaction;
import com.example.content.entity.InteractionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    Optional<Interaction> findByUserIdAndPostIdAndType(UUID userId, UUID postId, InteractionType type);

    boolean existsByUserIdAndPostIdAndType(UUID userId, UUID postId, InteractionType type);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Interaction i WHERE i.userId = :userId AND i.postId = :postId AND i.type = :type")
    void deleteByUserIdAndPostIdAndType(
            @Param("userId") UUID userId,
            @Param("postId") UUID postId,
            @Param("type") InteractionType type
    );

    @Query("SELECT i.postId FROM Interaction i WHERE i.userId = :userId AND i.type = :type AND i.postId IN :postIds")
    Set<UUID> findPostIdsByUserIdAndTypeAndPostIdIn(
            @Param("userId") UUID userId,
            @Param("type") InteractionType type,
            @Param("postIds") List<UUID> postIds
    );
}
