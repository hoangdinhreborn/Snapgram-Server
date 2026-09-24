package com.example.auth.repository;

import com.example.auth.entity.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Full-text search on username and display_name using PostgreSQL trigram index.
     * Results ordered by relevance (similarity to query string).
     */
    @Query("""
            SELECT u FROM AuthUser u
            WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY u.username ASC
            """)
    Page<AuthUser> searchByUsernameOrDisplayName(@Param("q") String q, Pageable pageable);
}
