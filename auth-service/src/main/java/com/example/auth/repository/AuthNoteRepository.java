package com.example.auth.repository;

import com.example.auth.entity.AuthNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuthNoteRepository extends JpaRepository<AuthNote, UUID> {

    List<AuthNote> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuthNote> findAllByExpiresAtBefore(Instant time);
}