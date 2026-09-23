package com.example.auth.repository;

import com.example.auth.entity.AuthUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRole, UUID> {

    List<AuthUserRole> findByUserId(UUID userId);

    boolean existsByUserIdAndRole(UUID userId, String role);

    void deleteByUserIdAndRole(UUID userId, String role);
}
