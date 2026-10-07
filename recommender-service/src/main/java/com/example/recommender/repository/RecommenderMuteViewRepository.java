package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderMuteView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecommenderMuteViewRepository extends JpaRepository<RecommenderMuteView, RecommenderMuteView.RecommenderMuteId> {

    @Query("SELECT m.id.mutedId FROM RecommenderMuteView m WHERE m.id.muterId = :userId")
    List<UUID> findMutedUserIds(@Param("userId") UUID userId);

    void deleteByIdMuterIdAndIdMutedId(UUID muterId, UUID mutedId);
}
