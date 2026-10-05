package com.example.auth.service;

import com.example.auth.dto.AdminDashboardResponse;
import com.example.auth.dto.AdminUserResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final AuthUserRepository userRepository;
    private final RoleService roleService;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        Instant startOfToday = todayUtc.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByBannedFalse();
        long bannedUsers = userRepository.countByBannedTrue();

        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);
        long newUsersThisWeek = userRepository.countByCreatedAtAfter(sevenDaysAgo);

        long emailVerifiedUsers = userRepository.countByEmailVerifiedTrue();
        long twoFaEnabledUsers = userRepository.countByTwoFaEnabledTrue();

        List<AdminUserResponse> recentUsers = userRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toAdminUserResponse)
                .toList();

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .bannedUsers(bannedUsers)
                .newUsersToday(newUsersToday)
                .newUsersThisWeek(newUsersThisWeek)
                .emailVerifiedUsers(emailVerifiedUsers)
                .twoFaEnabledUsers(twoFaEnabledUsers)
                .recentUsers(recentUsers)
                .build();
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
