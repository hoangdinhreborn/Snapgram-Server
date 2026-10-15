package com.example.auth.controller;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.dto.AssignRoleRequest;
import com.example.auth.dto.BanUserRequest;
import com.example.auth.dto.BootstrapAdminRequest;
import com.example.auth.security.SecurityUtils;
import com.example.auth.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "username", "email", "lastSeenAt", "displayName"
    );

    private final AdminService adminService;

    /**
     * GET /api/auth/admin/users?q=...&status=...&role=...&sort=createdAt,desc&page=0&size=20
     */
    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> getUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));

        return ResponseEntity.ok(adminService.getUsers(q, status, role, pageable));
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            field = "createdAt";
        }
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    /**
     * GET /api/auth/admin/users/{userId}
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    /**
     * POST /api/auth/admin/users/{userId}/ban
     */
    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<AdminUserResponse> banUser(
            @PathVariable UUID userId,
            @Valid @RequestBody BanUserRequest request) {
        UUID currentAdminId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(adminService.banUser(currentAdminId, userId, request.getReason()));
    }

    /**
     * POST /api/auth/admin/users/{userId}/unban
     */
    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<AdminUserResponse> unbanUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.unbanUser(userId));
    }

    /**
     * POST /api/auth/admin/users/{userId}/roles
     */
    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponse> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        return ResponseEntity.ok(adminService.assignRole(userId, request.getRole()));
    }

    /**
     * DELETE /api/auth/admin/users/{userId}/roles/{role}
     */
    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<AdminUserResponse> removeRole(
            @PathVariable UUID userId,
            @PathVariable String role) {
        return ResponseEntity.ok(adminService.removeRole(userId, role));
    }

    /**
     * POST /api/auth/admin/bootstrap
     * Public endpoint protected by secretKey to bootstrap the first admin user.
     */
    @PostMapping("/bootstrap")
    public ResponseEntity<AdminUserResponse> bootstrapAdmin(
            @Valid @RequestBody BootstrapAdminRequest request) {
        return ResponseEntity.ok(adminService.bootstrapAdmin(request.getUsernameOrEmail(), request.getSecretKey()));
    }
}
