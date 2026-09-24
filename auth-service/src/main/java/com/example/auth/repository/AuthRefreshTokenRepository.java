package com.example.auth.repository;

import com.example.auth.entity.AuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthRefreshTokenRepository
        extends JpaRepository<AuthRefreshToken, UUID> {

    Optional<AuthRefreshToken> findByJti(String jti);

    List<AuthRefreshToken> findAllByUserId(UUID userId);

    List<AuthRefreshToken> findAllByUserIdAndRevokedAtIsNull(UUID userId);

    /** Alias used by UserService to revoke all sessions on password change. */
    default List<AuthRefreshToken> findActiveByUserId(UUID userId) {
        return findAllByUserIdAndRevokedAtIsNull(userId);
    }

    void deleteAllByUserId(UUID userId);
}