package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.service.AdminService;
import com.example.auth.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
@Tag(name = "Admin RBAC API", description = "Quản lý vai trò động (Dynamic Roles), danh mục quyền (Permissions) và phân quyền người dùng")
public class RbacAdminController {

    private final RbacService rbacService;
    private final AdminService adminService;

    // ─────────────────────────────────────────────────────────────
    // Roles Management
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Lấy danh sách tất cả vai trò kèm quyền hạn")
    @GetMapping("/roles")
    public ResponseEntity<List<RoleDetailResponse>> getRoles() {
        return ResponseEntity.ok(rbacService.getAllRoles());
    }

    @Operation(summary = "Xem chi tiết một vai trò")
    @GetMapping("/roles/{roleId}")
    public ResponseEntity<RoleDetailResponse> getRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(rbacService.getRoleById(roleId));
    }

    @Operation(summary = "Tạo vai trò động mới (MODERATOR, SUPPORT, v.v.)")
    @PostMapping("/roles")
    public ResponseEntity<RoleDetailResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rbacService.createRole(request));
    }

    @Operation(summary = "Cập nhật thông tin vai trò")
    @PutMapping("/roles/{roleId}")
    public ResponseEntity<RoleDetailResponse> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(rbacService.updateRole(roleId, request));
    }

    @Operation(summary = "Xóa vai trò động (không thể xóa vai trò hệ thống)")
    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID roleId) {
        rbacService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Permissions Management
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Lấy danh mục tất cả quyền hạn gom theo nhóm")
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, List<PermissionResponse>>> getPermissions() {
        return ResponseEntity.ok(rbacService.getAllPermissionsGrouped());
    }

    @Operation(summary = "Cập nhật danh sách quyền hạn cho một vai trò")
    @PutMapping("/roles/{roleId}/permissions")
    public ResponseEntity<RoleDetailResponse> setRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody SetRolePermissionsRequest request) {
        return ResponseEntity.ok(rbacService.setRolePermissions(roleId, request.getPermissionIds()));
    }

    // ─────────────────────────────────────────────────────────────
    // User Roles & Effective Permissions
    // ─────────────────────────────────────────────────────────────

    @Operation(summary = "Lấy danh sách vai trò và quyền hiệu dụng của người dùng")
    @GetMapping("/users/{userId}/roles")
    public ResponseEntity<UserRolesPermissionsResponse> getUserRolesAndPermissions(@PathVariable UUID userId) {
        return ResponseEntity.ok(rbacService.getUserRolesAndPermissions(userId));
    }

    @Operation(summary = "Đồng bộ danh sách vai trò của người dùng")
    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponse> syncUserRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody SyncUserRolesRequest request) {
        return ResponseEntity.ok(adminService.syncUserRoles(userId, request.getRoles()));
    }
}
