package com.example.auth.repository;

import com.example.auth.entity.AuthBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuthBlockRepository extends JpaRepository<AuthBlock, UUID> {

    boolean existsByBlockerIdAndBlockedId(
            UUID blockerId,
            UUID blockedId
    );

    void deleteByBlockerIdAndBlockedId(
            UUID blockerId,
            UUID blockedId
    );
}