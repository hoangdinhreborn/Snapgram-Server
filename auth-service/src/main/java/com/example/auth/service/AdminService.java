package com.example.auth.service;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.entity.AuthRefreshToken;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AuthUserRepository userRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final RoleService roleService;

    @Value("${app.admin.bootstrap-secret:snapgram-admin-bootstrap-secret}")
    private String bootstrapSecret;

    /**
     * Get all users for admin with pagination.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(this::toAdminUserResponse);
    }

    /**
     * Get a specific user for admin.
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        return toAdminUserResponse(user);
    }

    /**
     * Ban user: mark banned, record reason, and revoke active refresh tokens.
     */
    @Transactional
    public AdminUserResponse banUser(UUID currentAdminId, UUID userId, String reason) {
        if (currentAdminId != null && currentAdminId.equals(userId)) {
            throw new IllegalArgumentException("Cannot ban yourself");
        }

        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        user.setBanned(true);
        user.setBannedAt(Instant.now());
        user.setBanReason(reason);
        AuthUser updated = userRepository.save(user);

        // Revoke all active sessions
        List<AuthRefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        if (!activeTokens.isEmpty()) {
            activeTokens.forEach(t -> t.setRevokedAt(Instant.now()));
            refreshTokenRepository.saveAll(activeTokens);
            log.info("Revoked {} active refresh tokens for banned user {}", activeTokens.size(), userId);
        }

        log.info("User {} has been banned by admin {}. Reason: {}", userId, currentAdminId, reason);
        return toAdminUserResponse(updated);
    }

    /**
     * Unban user: clear banned flag.
     */
    @Transactional
    public AdminUserResponse unbanUser(UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        user.setBanned(false);
        user.setBannedAt(null);
        user.setBanReason(null);
        AuthUser updated = userRepository.save(user);

        log.info("User {} has been unbanned", userId);
        return toAdminUserResponse(updated);
    }

    /**
     * Assign a role to user (e.g. ADMIN, MODERATOR).
     */
    @Transactional
    public AdminUserResponse assignRole(UUID userId, String role) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        roleService.assignRole(userId, role.toUpperCase());
        log.info("Assigned role {} to user {}", role.toUpperCase(), userId);
        return getUser(userId);
    }

    /**
     * Remove a role from user.
     */
    @Transactional
    public AdminUserResponse removeRole(UUID userId, String role) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        roleService.removeRole(userId, role.toUpperCase());
        log.info("Removed role {} from user {}", role.toUpperCase(), userId);
        return getUser(userId);
    }

    @Transactional
    public AdminUserResponse syncUserRoles(UUID userId, List<String> roles) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        roleService.syncUserRoles(userId, roles);
        log.info("Synced roles for user {}: {}", userId, roles);
        return getUser(userId);
    }

    /**
     * Bootstrap the first admin account using a shared secret.
     */
    @Transactional
    public AdminUserResponse bootstrapAdmin(String usernameOrEmail, String secretKey) {
        if (!bootstrapSecret.equals(secretKey)) {
            throw new IllegalArgumentException("Invalid bootstrap secret key");
        }

        AuthUser user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UserNotFoundException("User not found with username/email: " + usernameOrEmail));

        roleService.makeAdmin(user.getId());
        log.info("Bootstrapped ADMIN role for user {} ({})", user.getUsername(), user.getId());
        return toAdminUserResponse(user);
    }

    private AdminUserResponse toAdminUserResponse(AuthUser user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .privateAccount(user.isPrivateAccount())
                .emailVerified(user.isEmailVerified())
                .twoFaEnabled(user.isTwoFaEnabled())
                .banned(user.isBanned())
                .bannedAt(user.getBannedAt())
                .banReason(user.getBanReason())
                .lastSeenAt(user.getLastSeenAt())
                .createdAt(user.getCreatedAt())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .roles(roleService.getUserRoles(user.getId()))
                .build();
    }
}
