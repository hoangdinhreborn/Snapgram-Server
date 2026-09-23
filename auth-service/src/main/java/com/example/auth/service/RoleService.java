package com.example.auth.service;

import com.example.auth.entity.AuthUserRole;
import com.example.auth.repository.AuthUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final AuthUserRoleRepository roleRepository;

    /**
     * Get all roles for a user
     */
    public Collection<String> getUserRoles(UUID userId) {
        return roleRepository.findByUserId(userId)
                .stream()
                .map(AuthUserRole::getRole)
                .collect(Collectors.toList());
    }

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(UUID userId, String role) {
        return roleRepository.existsByUserIdAndRole(userId, role);
    }

    /**
     * Check if user has ADMIN role
     */
    public boolean isAdmin(UUID userId) {
        return hasRole(userId, "ADMIN");
    }

    /**
     * Assign role to user
     */
    @Transactional
    public void assignRole(UUID userId, String role) {
        if (!roleRepository.existsByUserIdAndRole(userId, role)) {
            AuthUserRole userRole = new AuthUserRole();
            userRole.setUserId(userId);
            userRole.setRole(role);
            userRole.setCreatedAt(Instant.now());
            roleRepository.save(userRole);
        }
    }

    /**
     * Remove role from user
     */
    @Transactional
    public void removeRole(UUID userId, String role) {
        roleRepository.deleteByUserIdAndRole(userId, role);
    }

    /**
     * Assign ADMIN role to user
     */
    @Transactional
    public void makeAdmin(UUID userId) {
        assignRole(userId, "ADMIN");
    }

    /**
     * Remove ADMIN role from user (demote to USER)
     */
    @Transactional
    public void removeAdmin(UUID userId) {
        removeRole(userId, "ADMIN");
    }
}
