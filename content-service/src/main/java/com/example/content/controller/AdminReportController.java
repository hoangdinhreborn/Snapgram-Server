package com.example.content.controller;

import com.example.content.dto.ReportResponse;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.service.ReportService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports API", description = "Quản lý và kiểm duyệt báo cáo vi phạm")
public class AdminReportController {

    private final ReportService reportService;

    @Operation(summary = "Lấy danh sách báo cáo vi phạm (Admin)")
    @GetMapping
    public ResponseEntity<Page<ReportResponse>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserContext.requireAdmin();

        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(reportService.getReports(status, targetType, pageable));
    }

    @Operation(summary = "Xem chi tiết một báo cáo vi phạm (Admin)")
    @GetMapping("/{id}")
    public ResponseEntity<ReportResponse> getReportById(@PathVariable UUID id) {
        UserContext.requireAdmin();
        return ResponseEntity.ok(reportService.getReportById(id));
    }

    @Operation(summary = "Duyệt báo cáo và xử lý nội dung vi phạm (Admin)")
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<ReportResponse> resolveReport(@PathVariable UUID id) {
        UserContext.requireAdmin();
        UUID adminId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(reportService.resolveReport(id, adminId));
    }

    @Operation(summary = "Bác bỏ báo cáo không vi phạm (Admin)")
    @PatchMapping("/{id}/dismiss")
    public ResponseEntity<ReportResponse> dismissReport(@PathVariable UUID id) {
        UserContext.requireAdmin();
        UUID adminId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(reportService.dismissReport(id, adminId));
    }
}
