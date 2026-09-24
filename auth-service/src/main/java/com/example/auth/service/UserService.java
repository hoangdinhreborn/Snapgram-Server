package com.example.auth.service;

import com.example.auth.dto.*;
import com.example.auth.entity.AuthBlock;
import com.example.auth.entity.AuthNote;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.repository.AuthBlockRepository;
import com.example.auth.repository.AuthNoteRepository;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final AuthUserRepository userRepository;
    private final AuthBlockRepository blockRepository;
    private final AuthNoteRepository noteRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────
    // Profile
    // ─────────────────────────────────────────────────────────────

    /** Full profile for the authenticated user themselves. */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        AuthUser user = getUser(userId);
        return toFullProfile(user);
    }

    /** Public profile for viewing another user's page. */
    @Transactional(readOnly = true)
    public UserProfileResponse getPublicProfile(UUID viewerId, String username) {
        AuthUser target = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // If either side has blocked the other → 404
        if (isMutuallyBlocked(viewerId, target.getId())) {
            throw new UserNotFoundException("User not found");
        }

        return toPublicProfile(target, viewerId.equals(target.getId()));
    }

    /** PATCH — only update fields that are non-null in the request. */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        AuthUser user = getUser(userId);

        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getAvatarUrl()   != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getBio()         != null) user.setBio(request.getBio());
        if (request.getWebsite()     != null) user.setWebsite(request.getWebsite());
        if (request.getPronouns()    != null) user.setPronouns(request.getPronouns());
        if (request.getCategory()    != null) user.setCategory(request.getCategory());

        userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return toFullProfile(user);
    }

    /** Search users by username / displayName (paginated). */
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> searchUsers(String q, Pageable pageable) {
        return userRepository.searchByUsernameOrDisplayName(q.trim(), pageable)
                .map(u -> UserProfileResponse.builder()
                        .id(u.getId().toString())
                        .username(u.getUsername())
                        .displayName(u.getDisplayName())
                        .avatarUrl(u.getAvatarUrl())
                        .privateAccount(u.isPrivateAccount())
                        .createdAt(u.getCreatedAt())
                        .build());
    }

    // ─────────────────────────────────────────────────────────────
    // Privacy
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public UserProfileResponse updatePrivacy(UUID userId, PrivacySettingRequest request) {
        AuthUser user = getUser(userId);
        user.setPrivateAccount(request.getPrivateAccount());
        userRepository.save(user);
        log.info("Privacy set to {} for user {}", request.getPrivateAccount(), userId);
        return toFullProfile(user);
    }

    // ─────────────────────────────────────────────────────────────
    // Password
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = getUser(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all existing refresh tokens so all other sessions are invalidated
        revokeAllRefreshTokens(userId);
        log.info("Password changed and all sessions invalidated for user {}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    // Activity Status
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public UserProfileResponse updateActivityStatus(UUID userId, ActivityStatusRequest request) {
        AuthUser user = getUser(userId);
        user.setShowActivityStatus(request.getShowActivityStatus());
        userRepository.save(user);
        log.info("Activity status set to {} for user {}", request.getShowActivityStatus(), userId);
        return toFullProfile(user);
    }

    @Transactional
    public void heartbeat(UUID userId) {
        AuthUser user = getUser(userId);
        user.setLastSeenAt(Instant.now());
        userRepository.save(user);
    }

    // ─────────────────────────────────────────────────────────────
    // Block / Unblock
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void blockUser(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        // Ensure target exists
        if (!userRepository.existsById(targetId)) {
            throw new UserNotFoundException("User not found");
        }
        // Idempotent — ignore if already blocked
        if (!blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            AuthBlock block = new AuthBlock();
            block.setBlockerId(blockerId);
            block.setBlockedId(targetId);
            blockRepository.save(block);
            log.info("User {} blocked user {}", blockerId, targetId);
        }
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot unblock yourself");
        }
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetId);
        log.info("User {} unblocked user {}", blockerId, targetId);
    }

    @Transactional(readOnly = true)
    public Page<BlockedUserResponse> getBlockedUsers(UUID blockerId, Pageable pageable) {
        return blockRepository.findAllByBlockerIdOrderByCreatedAtDesc(blockerId, pageable)
                .map(block -> {
                    AuthUser blocked = userRepository.findById(block.getBlockedId())
                            .orElse(null);
                    if (blocked == null) return null;
                    return BlockedUserResponse.builder()
                            .userId(blocked.getId().toString())
                            .username(blocked.getUsername())
                            .displayName(blocked.getDisplayName())
                            .avatarUrl(blocked.getAvatarUrl())
                            .blockedAt(block.getCreatedAt())
                            .build();
                })
                .map(r -> r); // keeps Page type; nulls are filtered at controller layer
    }

    // ─────────────────────────────────────────────────────────────
    // Note
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public NoteResponse upsertNote(UUID userId, NoteRequest request) {
        // Ensure user exists
        getUser(userId);

        // Delete existing notes for this user (only one active note allowed)
        List<AuthNote> existing = noteRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (!existing.isEmpty()) {
            noteRepository.deleteAll(existing);
        }

        AuthNote note = new AuthNote();
        note.setUserId(userId);
        note.setContent(request.getContent());
        note.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        noteRepository.save(note);

        log.info("Note upserted for user {}", userId);
        return toNoteResponse(note);
    }

    @Transactional
    public void deleteNote(UUID userId) {
        List<AuthNote> notes = noteRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        if (!notes.isEmpty()) {
            noteRepository.deleteAll(notes);
            log.info("Note deleted for user {}", userId);
        }
    }

    @Transactional(readOnly = true)
    public NoteResponse getActiveNote(UUID userId) {
        return noteRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(n -> n.getExpiresAt().isAfter(Instant.now()))
                .findFirst()
                .map(this::toNoteResponse)
                .orElse(null); // null → controller returns 204
    }

    // ─────────────────────────────────────────────────────────────
    // Scheduled cleanup
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteExpiredNotes() {
        List<AuthNote> expired = noteRepository.findAllByExpiresAtBefore(Instant.now());
        if (!expired.isEmpty()) {
            noteRepository.deleteAll(expired);
            log.info("Deleted {} expired note(s)", expired.size());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private AuthUser getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private boolean isMutuallyBlocked(UUID a, UUID b) {
        return blockRepository
                .existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(a, b, b, a);
    }

    private void revokeAllRefreshTokens(UUID userId) {
        Instant now = Instant.now();
        refreshTokenRepository.findActiveByUserId(userId)
                .forEach(token -> {
                    token.setRevokedAt(now);
                    refreshTokenRepository.save(token);
                });
    }

    /** Full profile — owner view (includes sensitive settings). */
    private UserProfileResponse toFullProfile(AuthUser u) {
        return UserProfileResponse.builder()
                .id(u.getId().toString())
                .username(u.getUsername())
                .email(u.getEmail())
                .displayName(u.getDisplayName())
                .avatarUrl(u.getAvatarUrl())
                .bio(u.getBio())
                .website(u.getWebsite())
                .pronouns(u.getPronouns())
                .category(u.getCategory())
                .privateAccount(u.isPrivateAccount())
                .emailVerified(u.isEmailVerified())
                .twoFaEnabled(u.isTwoFaEnabled())
                .showActivityStatus(u.isShowActivityStatus())
                .lastSeenAt(u.isShowActivityStatus() ? u.getLastSeenAt() : null)
                .createdAt(u.getCreatedAt())
                .build();
    }

    /** Public profile — respects privacy settings. */
    private UserProfileResponse toPublicProfile(AuthUser u, boolean isOwner) {
        boolean isPrivate = u.isPrivateAccount() && !isOwner;
        return UserProfileResponse.builder()
                .id(u.getId().toString())
                .username(u.getUsername())
                .displayName(u.getDisplayName())
                .avatarUrl(u.getAvatarUrl())
                .privateAccount(u.isPrivateAccount())
                .createdAt(u.getCreatedAt())
                // Hidden fields when account is private and viewer is not owner
                .bio(isPrivate ? null : u.getBio())
                .website(isPrivate ? null : u.getWebsite())
                .pronouns(isPrivate ? null : u.getPronouns())
                .category(isPrivate ? null : u.getCategory())
                // Activity status
                .lastSeenAt(u.isShowActivityStatus() ? u.getLastSeenAt() : null)
                .build();
    }

    private NoteResponse toNoteResponse(AuthNote note) {
        return NoteResponse.builder()
                .id(note.getId().toString())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .expiresAt(note.getExpiresAt())
                .build();
    }
}
