package com.example.auth.controller;

import com.example.auth.dto.AdminDashboardResponse;
import com.example.auth.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard API", description = "Các chỉ số thống kê tổng quan dành riêng cho Quản trị viên")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "Lấy dữ liệu thống kê tổng quan (Dashboard Stats)")
    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardResponse> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }
}
