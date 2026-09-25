package com.example.auth.repository;

import com.example.auth.entity.AuthPasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthPasswordResetRepository extends JpaRepository<AuthPasswordReset, UUID> {

    Optional<AuthPasswordReset> findByTokenHash(String tokenHash);

    /** Invalidate all unused tokens for this user before issuing a new one. */
    @Modifying
    @Query("""
            UPDATE AuthPasswordReset r
            SET r.usedAt = :now
            WHERE r.userId = :userId
              AND r.usedAt IS NULL
              AND r.expiresAt > :now
            """)
    void invalidateActiveTokens(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Cleanup: delete all expired tokens (run periodically). */
    @Modifying
    @Query("DELETE FROM AuthPasswordReset r WHERE r.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
