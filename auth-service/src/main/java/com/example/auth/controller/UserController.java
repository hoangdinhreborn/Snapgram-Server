package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.service.TwoFaService;
import com.example.auth.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final TwoFaService twoFaService;

    // ─────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/users/me
     * Returns the authenticated user's full profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(userService.getMyProfile(userId));
    }

    /**
     * PATCH /api/users/me
     * Update own profile fields (only non-null fields are applied).
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /**
     * GET /api/users/{username}
     * View another user's public profile (privacy rules applied).
     */
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getPublicProfile(
            @AuthenticationPrincipal UUID viewerId,
            @PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicProfile(viewerId, username));
    }

    /**
     * GET /api/users/search?q=foo&page=0&size=20
     * Search users by username or displayName.
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UserProfileResponse>> searchUsers(
            @RequestParam
            @NotBlank(message = "Search query must not be blank")
            @Size(min = 1, max = 50, message = "Search query must be between 1 and 50 characters")
            String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50); // cap at 50
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.searchUsers(q, pageable));
    }

    // ─────────────────────────────────────────────────────────────
    // Privacy
    // ─────────────────────────────────────────────────────────────

    /**
     * PUT /api/users/me/privacy
     * Toggle private/public account.
     */
    @PutMapping("/me/privacy")
    public ResponseEntity<UserProfileResponse> updatePrivacy(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PrivacySettingRequest request) {
        return ResponseEntity.ok(userService.updatePrivacy(userId, request));
    }

    // ─────────────────────────────────────────────────────────────
    // Password
    // ─────────────────────────────────────────────────────────────

    /**
     * PUT /api/users/me/password
     * Change password — verifies current password, revokes all sessions.
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Two-Factor Authentication
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/users/me/2fa/setup
     * Generate TOTP secret + QR code. Does NOT enable 2FA yet.
     */
    @PostMapping("/me/2fa/setup")
    public ResponseEntity<TwoFaSetupResponse> setup2Fa(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(twoFaService.setup(userId));
    }

    /**
     * POST /api/users/me/2fa/enable
     * Confirm TOTP code → enable 2FA + receive backup codes.
     */
    @PostMapping("/me/2fa/enable")
    public ResponseEntity<TwoFaEnableResponse> enable2Fa(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody TwoFaEnableRequest request) {
        return ResponseEntity.ok(twoFaService.enable(userId, request));
    }

    /**
     * DELETE /api/users/me/2fa/disable
     * Disable 2FA — requires valid TOTP or backup code.
     */
    @DeleteMapping("/me/2fa/disable")
    public ResponseEntity<Void> disable2Fa(
            @AuthenticationPrincipal UUID userId,
            @RequestBody TwoFaDisableRequest request) {
        twoFaService.disable(userId, request);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Activity Status
    // ─────────────────────────────────────────────────────────────

    /**
     * PUT /api/users/me/activity-status
     * Toggle whether last-seen is visible to others.
     */
    @PutMapping("/me/activity-status")
    public ResponseEntity<UserProfileResponse> updateActivityStatus(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ActivityStatusRequest request) {
        return ResponseEntity.ok(userService.updateActivityStatus(userId, request));
    }

    /**
     * POST /api/users/me/heartbeat
     * Update last_seen_at to now (called periodically by client).
     */
    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @AuthenticationPrincipal UUID userId) {
        userService.heartbeat(userId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Block / Unblock
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/users/{userId}/block
     */
    @PostMapping("/{targetId}/block")
    public ResponseEntity<Void> blockUser(
            @AuthenticationPrincipal UUID blockerId,
            @PathVariable UUID targetId) {
        userService.blockUser(blockerId, targetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/users/{userId}/block
     */
    @DeleteMapping("/{targetId}/block")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal UUID blockerId,
            @PathVariable UUID targetId) {
        userService.unblockUser(blockerId, targetId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/users/me/blocks?page=0&size=20
     */
    @GetMapping("/me/blocks")
    public ResponseEntity<Page<BlockedUserResponse>> getBlockedUsers(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getBlockedUsers(userId, pageable));
    }

    // ─────────────────────────────────────────────────────────────
    // Note
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/users/me/note
     * Create or replace the active note (max 60 chars, TTL 24h).
     */
    @PostMapping("/me/note")
    public ResponseEntity<NoteResponse> upsertNote(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(userService.upsertNote(userId, request));
    }

    /**
     * DELETE /api/users/me/note
     */
    @DeleteMapping("/me/note")
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal UUID userId) {
        userService.deleteNote(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/users/{userId}/note
     * Get the active note of any user (null → 204 if expired / no note).
     */
    @GetMapping("/{userId}/note")
    public ResponseEntity<NoteResponse> getNote(@PathVariable UUID userId) {
        NoteResponse note = userService.getActiveNote(userId);
        return note != null
                ? ResponseEntity.ok(note)
                : ResponseEntity.noContent().build();
    }
}
