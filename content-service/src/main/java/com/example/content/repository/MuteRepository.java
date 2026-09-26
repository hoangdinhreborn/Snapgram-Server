package com.example.content.repository;

import com.example.content.entity.Mute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MuteRepository extends JpaRepository<Mute, UUID> {

    Optional<Mute> findByMuterIdAndMutedId(UUID muterId, UUID mutedId);

    boolean existsByMuterIdAndMutedId(UUID muterId, UUID mutedId);

    void deleteByMuterIdAndMutedId(UUID muterId, UUID mutedId);

    @Query("SELECT m.mutedId FROM Mute m WHERE m.muterId = :muterId")
    List<UUID> findMutedIds(@Param("muterId") UUID muterId);
}
