package com.example.recommender.controller;

import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
import com.example.recommender.service.BatchJobService;
import com.example.recommender.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/batch")
@RequiredArgsConstructor
@Tag(name = "Admin Batch API", description = "Các API điều khiển và giám sát Spring Batch (Admin Only)")
public class AdminBatchController {

    private final BatchJobService batchJobService;

    @Operation(summary = "Kích hoạt thủ công một Spring Batch Job")
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<JobExecutionResponse> runJob(@PathVariable String jobName) throws Exception {
        UserContext.requireAdmin();
        return ResponseEntity.ok(batchJobService.runJob(jobName));
    }

    @Operation(summary = "Xem trạng thái & số liệu chi tiết của một lần chạy (Execution ID)")
    @GetMapping("/executions/{id}")
    public ResponseEntity<JobExecutionResponse> getExecution(@PathVariable Long id) {
        UserContext.requireAdmin();
        return ResponseEntity.ok(batchJobService.getExecution(id));
    }

    @Operation(summary = "Xem lịch sử các lần thực thi gần nhất của toàn bộ Batch Jobs")
    @GetMapping("/executions")
    public ResponseEntity<JobHistoryResponse> getExecutionHistory(
            @RequestParam(defaultValue = "10") int limit) {
        UserContext.requireAdmin();
        return ResponseEntity.ok(batchJobService.getExecutionHistory(limit));
    }

    @Operation(summary = "Khởi động lại (Restart) một JobExecution bị FAILED từ điểm dừng an toàn")
    @PostMapping("/executions/{id}/restart")
    public ResponseEntity<JobExecutionResponse> restartJob(@PathVariable Long id) throws Exception {
        UserContext.requireAdmin();
        return ResponseEntity.ok(batchJobService.restartJob(id));
    }

    @Operation(summary = "Dừng (Stop) một JobExecution đang chạy")
    @PostMapping("/executions/{id}/stop")
    public ResponseEntity<JobExecutionResponse> stopJob(@PathVariable Long id) throws Exception {
        UserContext.requireAdmin();
        return ResponseEntity.ok(batchJobService.stopJob(id));
    }
}
