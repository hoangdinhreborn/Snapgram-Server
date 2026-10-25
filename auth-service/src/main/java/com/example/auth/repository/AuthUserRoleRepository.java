package com.example.auth.repository;

import com.example.auth.entity.AuthUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRole, UUID> {

    List<AuthUserRole> findByUserId(UUID userId);

    @Query("SELECT r.userId FROM AuthUserRole r WHERE UPPER(r.role) = UPPER(:role)")
    List<UUID> findUserIdsByRole(@Param("role") String role);

    @Query("SELECT DISTINCT r.userId FROM AuthUserRole r WHERE UPPER(r.role) != 'USER'")
    List<UUID> findNonStandardRoleUserIds();

    @Query("SELECT COUNT(r) > 0 FROM AuthUserRole r WHERE r.userId = :userId AND UPPER(r.role) != 'USER'")
    boolean hasNonStandardRole(@Param("userId") UUID userId);

    boolean existsByUserIdAndRole(UUID userId, String role);

    void deleteByUserIdAndRole(UUID userId, String role);

    void deleteByUserId(UUID userId);

    long countByRole(String role);
}
