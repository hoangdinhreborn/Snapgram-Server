package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.auth.security.SecurityUtils;
import com.example.auth.service.EmailVerificationService;
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
    private final EmailVerificationService emailVerificationService;

    // ─────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────

    /** GET /api/users/me */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile(currentUserId()));
    }

    /** PATCH /api/users/me */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(currentUserId(), request));
    }

    /** GET /api/users/{username} */
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileResponse> getPublicProfile(
            @PathVariable String username) {
        return ResponseEntity.ok(userService.getPublicProfile(currentUserId(), username));
    }

    /** GET /api/users/search?q=foo&page=0&size=20 */
    @GetMapping("/search")
    public ResponseEntity<Page<UserProfileResponse>> searchUsers(
            @RequestParam
            @NotBlank(message = "Search query must not be blank")
            @Size(min = 1, max = 50, message = "Search query must be between 1 and 50 characters")
            String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.searchUsers(q, pageable));
    }

    // ─────────────────────────────────────────────────────────────
    // Privacy
    // ─────────────────────────────────────────────────────────────

    /** PUT /api/users/me/privacy */
    @PutMapping("/me/privacy")
    public ResponseEntity<UserProfileResponse> updatePrivacy(
            @Valid @RequestBody PrivacySettingRequest request) {
        return ResponseEntity.ok(userService.updatePrivacy(currentUserId(), request));
    }

    // ─────────────────────────────────────────────────────────────
    // Password
    // ─────────────────────────────────────────────────────────────

    /** PUT /api/users/me/password */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Two-Factor Authentication
    // ─────────────────────────────────────────────────────────────

    /** POST /api/users/me/2fa/setup */
    @PostMapping("/me/2fa/setup")
    public ResponseEntity<TwoFaSetupResponse> setup2Fa() {
        return ResponseEntity.ok(twoFaService.setup(currentUserId()));
    }

    /** POST /api/users/me/2fa/enable */
    @PostMapping("/me/2fa/enable")
    public ResponseEntity<TwoFaEnableResponse> enable2Fa(
            @Valid @RequestBody TwoFaEnableRequest request) {
        return ResponseEntity.ok(twoFaService.enable(currentUserId(), request));
    }

    /** DELETE /api/users/me/2fa/disable */
    @DeleteMapping("/me/2fa/disable")
    public ResponseEntity<Void> disable2Fa(
            @RequestBody TwoFaDisableRequest request) {
        twoFaService.disable(currentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Activity Status
    // ─────────────────────────────────────────────────────────────

    /** PUT /api/users/me/activity-status */
    @PutMapping("/me/activity-status")
    public ResponseEntity<UserProfileResponse> updateActivityStatus(
            @Valid @RequestBody ActivityStatusRequest request) {
        return ResponseEntity.ok(userService.updateActivityStatus(currentUserId(), request));
    }

    /** POST /api/users/me/heartbeat */
    @PostMapping("/me/heartbeat")
    public ResponseEntity<Void> heartbeat() {
        userService.heartbeat(currentUserId());
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Block / Unblock
    // ─────────────────────────────────────────────────────────────

    /** POST /api/users/{targetId}/block */
    @PostMapping("/{targetId}/block")
    public ResponseEntity<Void> blockUser(@PathVariable UUID targetId) {
        userService.blockUser(currentUserId(), targetId);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/users/{targetId}/block */
    @DeleteMapping("/{targetId}/block")
    public ResponseEntity<Void> unblockUser(@PathVariable UUID targetId) {
        userService.unblockUser(currentUserId(), targetId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/users/me/blocks?page=0&size=20 */
    @GetMapping("/me/blocks")
    public ResponseEntity<Page<BlockedUserResponse>> getBlockedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(userService.getBlockedUsers(currentUserId(), pageable));
    }

    // ─────────────────────────────────────────────────────────────
    // Note
    // ─────────────────────────────────────────────────────────────

    /** POST /api/users/me/note */
    @PostMapping("/me/note")
    public ResponseEntity<NoteResponse> upsertNote(
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(userService.upsertNote(currentUserId(), request));
    }

    /** DELETE /api/users/me/note */
    @DeleteMapping("/me/note")
    public ResponseEntity<Void> deleteNote() {
        userService.deleteNote(currentUserId());
        return ResponseEntity.noContent().build();
    }

    /** GET /api/users/{userId}/note */
    @GetMapping("/{userId}/note")
    public ResponseEntity<NoteResponse> getNote(@PathVariable UUID userId) {
        NoteResponse note = userService.getActiveNote(userId);
        return note != null
                ? ResponseEntity.ok(note)
                : ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Email Verification & Change (requires auth)
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/users/me/send-verification-email
     * Resend verification email for the current (unverified) email.
     */
    @PostMapping("/me/send-verification-email")
    public ResponseEntity<Void> sendVerificationEmail() {
        emailVerificationService.sendVerificationEmail(currentUserId());
        return ResponseEntity.accepted().build();
    }

    /**
     * POST /api/users/me/email
     * Request an email address change (sends verification to new email).
     */
    @PostMapping("/me/email")
    public ResponseEntity<Void> requestEmailChange(
            @Valid @RequestBody ChangeEmailRequest request) {
        emailVerificationService.requestEmailChange(currentUserId(), request);
        return ResponseEntity.accepted().build();
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
