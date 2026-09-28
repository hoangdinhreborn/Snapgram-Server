package com.example.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminDashboardResponse {

    private long totalUsers;
    private long activeUsers;
    private long bannedUsers;

    private long newUsersToday;
    private long newUsersThisWeek;

    private long emailVerifiedUsers;
    private long twoFaEnabledUsers;

    private List<AdminUserResponse> recentUsers;
}
