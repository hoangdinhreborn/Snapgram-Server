package com.example.auth.repository;

import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID>, JpaSpecificationExecutor<AuthUser> {

    Optional<AuthUser> findByEmail(String email);

    Optional<AuthUser> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    long countByRole(Role role);

    long countByBannedFalse();

    long countByBannedTrue();

    long countByTwoFaEnabledTrue();

    long countByEmailVerifiedTrue();

    long countByCreatedAtAfter(Instant after);

    List<AuthUser> findTop5ByOrderByCreatedAtDesc();

    Page<AuthUser> findByBannedFalseOrderByCreatedAtDesc(Pageable pageable);

    @Query(
            value = """
                    SELECT u FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                    ORDER BY u.createdAt DESC
                    """,
            countQuery = """
                    SELECT count(u) FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                    """
    )
    Page<AuthUser> findActiveStandardUsersOrderByCreatedAtDesc(@Param("role") Role role, Pageable pageable);

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

    @Query(
            value = """
                    SELECT u FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND (
                          LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
                    ORDER BY u.username ASC
                    """,
            countQuery = """
                    SELECT count(u) FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND (
                          LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
                    """
    )
    Page<AuthUser> searchActiveStandardUsers(@Param("q") String q, @Param("role") Role role, Pageable pageable);

    @Query(
            value = """
                    SELECT u FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND u.id != :viewerId
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthBlock b
                          WHERE (b.blockerId = :viewerId AND b.blockedId = u.id)
                             OR (b.blockerId = u.id AND b.blockedId = :viewerId)
                      )
                    ORDER BY u.createdAt DESC
                    """,
            countQuery = """
                    SELECT count(u) FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND u.id != :viewerId
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthBlock b
                          WHERE (b.blockerId = :viewerId AND b.blockedId = u.id)
                             OR (b.blockerId = u.id AND b.blockedId = :viewerId)
                      )
                    """
    )
    Page<AuthUser> findActiveStandardUsersWithoutViewerOrderByCreatedAtDesc(
            @Param("role") Role role,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT u FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND u.id != :viewerId
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthBlock b
                          WHERE (b.blockerId = :viewerId AND b.blockedId = u.id)
                             OR (b.blockerId = u.id AND b.blockedId = :viewerId)
                      )
                      AND (
                          LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
                    ORDER BY u.username ASC
                    """,
            countQuery = """
                    SELECT count(u) FROM AuthUser u
                    WHERE u.banned = false
                      AND u.role = :role
                      AND u.id != :viewerId
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthUserRole ur
                          WHERE ur.userId = u.id AND UPPER(ur.role) != 'USER'
                      )
                      AND NOT EXISTS (
                          SELECT 1 FROM AuthBlock b
                          WHERE (b.blockerId = :viewerId AND b.blockedId = u.id)
                             OR (b.blockerId = u.id AND b.blockedId = :viewerId)
                      )
                      AND (
                          LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))
                      )
                    """
    )
    Page<AuthUser> searchActiveStandardUsersWithoutViewer(
            @Param("q") String q,
            @Param("role") Role role,
            @Param("viewerId") UUID viewerId,
            Pageable pageable
    );
}
