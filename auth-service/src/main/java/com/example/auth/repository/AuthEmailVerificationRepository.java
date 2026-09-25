package com.example.auth.repository;

import com.example.auth.entity.AuthEmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthEmailVerificationRepository extends JpaRepository<AuthEmailVerification, UUID> {

    Optional<AuthEmailVerification> findByTokenHash(String tokenHash);

    /** Invalidate all unused, non-expired tokens for this user before issuing a new one. */
    @Modifying
    @Query("""
            UPDATE AuthEmailVerification v
            SET v.usedAt = :now
            WHERE v.userId = :userId
              AND v.usedAt IS NULL
              AND v.expiresAt > :now
            """)
    void invalidateActiveTokens(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Cleanup: delete all expired tokens (run periodically). */
    @Modifying
    @Query("DELETE FROM AuthEmailVerification v WHERE v.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);
}
