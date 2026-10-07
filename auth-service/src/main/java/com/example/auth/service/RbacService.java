package com.example.auth.service;

import com.example.auth.dto.*;
import com.example.auth.entity.AuthPermission;
import com.example.auth.entity.AuthRole;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.DuplicateUserException;
import com.example.auth.exception.UserNotFoundException;
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
public class RbacService {

    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthUserRepository userRepository;

    /**
     * Get all roles with their permissions and user counts.
     */
    @Transactional(readOnly = true)
    public List<RoleDetailResponse> getAllRoles() {
        return roleRepository.findAllWithPermissions().stream()
                .map(this::toRoleDetailResponse)
                .toList();
    }

    /**
     * Get a specific role by ID.
     */
    @Transactional(readOnly = true)
    public RoleDetailResponse getRoleById(UUID roleId) {
        AuthRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new UserNotFoundException("Role not found with id: " + roleId));
        return toRoleDetailResponse(role);
    }

    /**
     * Create a new dynamic role.
     */
    @Transactional
    public RoleDetailResponse createRole(CreateRoleRequest request) {
        String roleName = request.getName().trim().toUpperCase();

        if (roleRepository.existsByName(roleName)) {
            throw new DuplicateUserException("Role already exists with name: " + roleName);
        }

        Set<AuthPermission> permissions = new HashSet<>();
        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            permissions.addAll(permissionRepository.findAllById(request.getPermissionIds()));
        }

        AuthRole role = AuthRole.builder()
                .name(roleName)
                .description(request.getDescription())
                .system(false)
                .permissions(permissions)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        AuthRole saved = roleRepository.save(role);
        log.info("Created dynamic role {} with {} permissions", saved.getName(), permissions.size());
        return toRoleDetailResponse(saved);
    }

    /**
     * Update an existing role.
     */
    @Transactional
    public RoleDetailResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        AuthRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new UserNotFoundException("Role not found with id: " + roleId));

        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim().toUpperCase();
            if (!newName.equals(role.getName())) {
                if (role.isSystem()) {
                    throw new IllegalArgumentException("Cannot rename system roles (" + role.getName() + ")");
                }
                if (roleRepository.existsByName(newName)) {
                    throw new DuplicateUserException("Role already exists with name: " + newName);
                }
                role.setName(newName);
            }
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription().trim());
        }

        AuthRole updated = roleRepository.save(role);
        log.info("Updated role {}", updated.getName());
        return toRoleDetailResponse(updated);
    }

    /**
     * Delete a dynamic role.
     */
    @Transactional
    public void deleteRole(UUID roleId) {
        AuthRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new UserNotFoundException("Role not found with id: " + roleId));

        if (role.isSystem()) {
            throw new IllegalArgumentException("Cannot delete system roles (" + role.getName() + ")");
        }

        long assignedCount = userRoleRepository.countByRole(role.getName());
        if (assignedCount > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete role " + role.getName() + ": it is currently assigned to " + assignedCount + " user(s). Revoke it first.");
        }

        roleRepository.delete(role);
        log.info("Deleted dynamic role {}", role.getName());
    }

    /**
     * Get all available permissions grouped by category.
     */
    @Transactional(readOnly = true)
    public Map<String, List<PermissionResponse>> getAllPermissionsGrouped() {
        return permissionRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(this::toPermissionResponse)
                .collect(Collectors.groupingBy(PermissionResponse::getCategory, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Set the permissions for a role.
     */
    @Transactional
    public RoleDetailResponse setRolePermissions(UUID roleId, List<UUID> permissionIds) {
        AuthRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new UserNotFoundException("Role not found with id: " + roleId));

        Set<AuthPermission> permissions = new HashSet<>();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            permissions.addAll(permissionRepository.findAllById(permissionIds));
        }

        role.setPermissions(permissions);
        AuthRole saved = roleRepository.save(role);
        log.info("Updated permissions for role {}: {} permissions assigned", saved.getName(), permissions.size());
        return toRoleDetailResponse(saved);
    }

    /**
     * Get effective roles and permissions for a user.
     */
    @Transactional(readOnly = true)
    public UserRolesPermissionsResponse getUserRolesAndPermissions(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        Set<String> roleNames = new LinkedHashSet<>();
        if (user.getRole() != null) {
            roleNames.add(user.getRole().name());
        }
        userRoleRepository.findByUserId(userId).forEach(ur -> roleNames.add(ur.getRole()));

        List<AuthRole> roles = roleRepository.findByNamesWithPermissions(roleNames);

        Set<String> effectivePermissions = new TreeSet<>();
        // If ADMIN, automatically has all permissions
        if (roleNames.contains("ADMIN")) {
            permissionRepository.findAll().forEach(p -> effectivePermissions.add(p.getName()));
        } else {
            for (AuthRole r : roles) {
                if (r.getPermissions() != null) {
                    r.getPermissions().forEach(p -> effectivePermissions.add(p.getName()));
                }
            }
        }

        return UserRolesPermissionsResponse.builder()
                .userId(userId)
                .primaryRole(user.getRole() != null ? user.getRole().name() : "USER")
                .roles(new ArrayList<>(roleNames))
                .effectivePermissions(new ArrayList<>(effectivePermissions))
                .build();
    }

    private RoleDetailResponse toRoleDetailResponse(AuthRole role) {
        long userCount = userRoleRepository.countByRole(role.getName());

        List<PermissionResponse> permissions = role.getPermissions() != null
                ? role.getPermissions().stream().map(this::toPermissionResponse).sorted(Comparator.comparing(PermissionResponse::getName)).toList()
                : Collections.emptyList();

        return RoleDetailResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystem(role.isSystem())
                .permissions(permissions)
                .userCount(userCount)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private PermissionResponse toPermissionResponse(AuthPermission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .category(permission.getCategory())
                .description(permission.getDescription())
                .build();
    }
}
