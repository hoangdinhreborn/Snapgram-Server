package com.example.content.controller;

import com.example.content.dto.CreateReportRequest;
import com.example.content.dto.ReportResponse;
import com.example.content.service.ReportService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports API", description = "Báo cáo nội dung vi phạm")
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Gửi báo cáo vi phạm")
    @PostMapping
    public ResponseEntity<ReportResponse> createReport(@Valid @RequestBody CreateReportRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(userId, request));
    }
}
