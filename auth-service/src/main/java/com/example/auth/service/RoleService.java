package com.example.auth.service;

import com.example.auth.entity.AuthPermission;
import com.example.auth.entity.AuthRole;
import com.example.auth.entity.AuthUser;
import com.example.auth.entity.AuthUserRole;
import com.example.auth.entity.Role;
import com.example.auth.repository.AuthPermissionRepository;
import com.example.auth.repository.AuthRoleRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.repository.AuthUserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final AuthUserRoleRepository roleRepository;
    private final AuthUserRepository userRepository;
    private final AuthRoleRepository authRoleRepository;
    private final AuthPermissionRepository permissionRepository;

    /**
     * Get all roles for a user (combining primary role and dynamic roles)
     */
    public Collection<String> getUserRoles(UUID userId) {
        Set<String> roles = new LinkedHashSet<>();
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getRole() != null) {
                roles.add(user.getRole().name());
            }
        });
        roleRepository.findByUserId(userId).forEach(ur -> roles.add(ur.getRole()));
        if (roles.isEmpty()) {
            roles.add("USER");
        }
        return roles;
    }

    /**
     * Get all effective permissions for a user across all assigned roles
     */
    public Collection<String> getUserPermissions(UUID userId) {
        Collection<String> roles = getUserRoles(userId);
        if (roles.contains("ADMIN")) {
            return permissionRepository.findAll().stream()
                    .map(AuthPermission::getName)
                    .collect(Collectors.toSet());
        }

        List<AuthRole> authRoles = authRoleRepository.findByNamesWithPermissions(roles);
        Set<String> permissions = new TreeSet<>();
        for (AuthRole role : authRoles) {
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(p -> permissions.add(p.getName()));
            }
        }
        return permissions;
    }

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(UUID userId, String role) {
        return getUserRoles(userId).stream()
                .anyMatch(r -> r.equalsIgnoreCase(role));
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
    public void assignRole(UUID userId, String roleName) {
        String normalized = roleName.trim().toUpperCase();

        // If assigning ADMIN, also update primary role on AuthUser
        if ("ADMIN".equals(normalized)) {
            userRepository.findById(userId).ifPresent(user -> {
                user.setRole(Role.ADMIN);
                userRepository.save(user);
            });
        }

        if (!roleRepository.existsByUserIdAndRole(userId, normalized)) {
            AuthUserRole userRole = new AuthUserRole();
            userRole.setUserId(userId);
            userRole.setRole(normalized);
            userRole.setCreatedAt(Instant.now());
            roleRepository.save(userRole);
            log.info("Assigned role {} to user {}", normalized, userId);
        }
    }

    /**
     * Remove role from user
     */
    @Transactional
    public void removeRole(UUID userId, String roleName) {
        String normalized = roleName.trim().toUpperCase();

        if ("ADMIN".equals(normalized)) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1 && hasRole(userId, "ADMIN")) {
                throw new IllegalArgumentException("Cannot remove ADMIN role: at least one administrator must remain in system");
            }
            userRepository.findById(userId).ifPresent(user -> {
                user.setRole(Role.USER);
                userRepository.save(user);
            });
        }

        roleRepository.deleteByUserIdAndRole(userId, normalized);
        log.info("Removed role {} from user {}", normalized, userId);
    }

    /**
     * Sync user roles (replace current roles with new list)
     */
    @Transactional
    public void syncUserRoles(UUID userId, List<String> newRoles) {
        Set<String> normalizedRoles = newRoles.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (normalizedRoles.isEmpty()) {
            normalizedRoles.add("USER");
        }

        // If removing ADMIN, check admin count
        if (hasRole(userId, "ADMIN") && !normalizedRoles.contains("ADMIN")) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new IllegalArgumentException("Cannot remove ADMIN role: at least one administrator must remain in system");
            }
        }

        // Update primary role
        userRepository.findById(userId).ifPresent(user -> {
            user.setRole(normalizedRoles.contains("ADMIN") ? Role.ADMIN : Role.USER);
            userRepository.save(user);
        });

        // Delete old roles and assign new ones
        roleRepository.deleteByUserId(userId);
        for (String r : normalizedRoles) {
            AuthUserRole ur = new AuthUserRole();
            ur.setUserId(userId);
            ur.setRole(r);
            ur.setCreatedAt(Instant.now());
            roleRepository.save(ur);
        }
        log.info("Synced roles for user {}: {}", userId, normalizedRoles);
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
