package com.example.recommender.controller;

import com.example.recommender.batch.config.DemoFailureSwitch;
import com.example.recommender.batch.service.DemoDataSeederService;
import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
import com.example.recommender.dto.batch.SeedDataResponse;
import com.example.recommender.dto.batch.StepExecutionDto;
import com.example.recommender.exception.JobConflictException;
import com.example.recommender.exception.ResourceNotFoundException;
import com.example.recommender.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/batch")
@Slf4j
@Tag(name = "Admin Batch API", description = "Các API điều khiển, giám sát và demo Spring Batch (Admin Only)")
public class AdminBatchController {

    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final ApplicationContext applicationContext;
    private final DemoDataSeederService demoDataSeederService;
    private final JobLauncher asyncJobLauncher;
    private final DemoFailureSwitch demoFailureSwitch;

    @Autowired
    public AdminBatchController(
            JobExplorer jobExplorer,
            JobOperator jobOperator,
            ApplicationContext applicationContext,
            DemoDataSeederService demoDataSeederService,
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher,
            DemoFailureSwitch demoFailureSwitch
    ) {
        this.jobExplorer = jobExplorer;
        this.jobOperator = jobOperator;
        this.applicationContext = applicationContext;
        this.demoDataSeederService = demoDataSeederService;
        this.asyncJobLauncher = asyncJobLauncher;
        this.demoFailureSwitch = demoFailureSwitch;
    }

    @Operation(summary = "Seed dữ liệu giả lập phục vụ demo Spring Batch (5.000 - 20.000 bản ghi)")
    @PostMapping("/demo/seed")
    public ResponseEntity<SeedDataResponse> seedDemoData(@RequestParam(defaultValue = "5000") int count) {
        UserContext.requireAdmin();
        return ResponseEntity.ok(demoDataSeederService.seedDemoData(count));
    }

    @Operation(summary = "Kích hoạt thủ công một Spring Batch Job")
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<JobExecutionResponse> runJob(
            @PathVariable String jobName,
            @RequestParam(required = false) Integer failAtItem) throws Exception {
        UserContext.requireAdmin();

        // 1. Kiểm tra Job Bean có tồn tại trong context không
        if (!applicationContext.containsBean(jobName)) {
            throw new ResourceNotFoundException("Không tìm thấy Job có tên '" + jobName + "'");
        }
        Object bean = applicationContext.getBean(jobName);
        if (!(bean instanceof Job job)) {
            throw new IllegalArgumentException("Bean '" + jobName + "' không phải là một Spring Batch Job");
        }

        // 2. Chống chạy trùng Job (Concurrency Protection)
        // Lưu ý: Cơ chế findRunningJobExecutions đủ cho kiến trúc Single-Instance.
        // Khi scale đa instance trong môi trường production, cần dùng Distributed Lock (như ShedLock hoặc Redis lock).
        Set<JobExecution> running = jobExplorer.findRunningJobExecutions(jobName);
        if (!running.isEmpty()) {
            throw new JobConflictException("Job '" + jobName + "' hiện đang có execution đang chạy (ID: "
                    + running.iterator().next().getId() + "). Vui lòng đợi hoàn tất trước khi chạy tiếp.");
        }

        // 3. Khởi tạo JobParameters & vũ trang DemoFailureSwitch nếu có failAtItem
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("triggerSource", "ADMIN_API");

        if (failAtItem != null && failAtItem > 0) {
            builder.addLong("failAtItem", failAtItem.longValue());
            demoFailureSwitch.arm(); // Bật cờ lỗi giả lập đúng 1 lần
            log.info("🎯 [DEMO RESTART] Đã vũ trang DemoFailureSwitch tại item #{}", failAtItem);
        } else {
            demoFailureSwitch.disarm();
        }

        JobParameters params = builder.toJobParameters();
        log.info("▶ [ADMIN MANUAL RUN] Khởi chạy job {} với params: {}", jobName, params);

        JobExecution execution = asyncJobLauncher.run(job, params);
        return ResponseEntity.ok(mapToResponse(execution));
    }

    @Operation(summary = "Xem trạng thái & số liệu chi tiết của một lần chạy (Execution ID)")
    @GetMapping("/executions/{id}")
    public ResponseEntity<JobExecutionResponse> getExecution(@PathVariable Long id) {
        UserContext.requireAdmin();
        JobExecution execution = jobExplorer.getJobExecution(id);
        if (execution == null) {
            throw new ResourceNotFoundException("Không tìm thấy JobExecution với ID: " + id);
        }
        return ResponseEntity.ok(mapToResponse(execution));
    }

    @Operation(summary = "Xem lịch sử các lần chạy gần nhất của các Job")
    @GetMapping("/executions")
    public ResponseEntity<JobHistoryResponse> getExecutionHistory(
            @RequestParam(required = false) String jobName,
            @RequestParam(defaultValue = "20") int limit) {
        UserContext.requireAdmin();
        limit = Math.min(Math.max(1, limit), 100);

        List<JobExecutionResponse> list = new ArrayList<>();
        List<String> jobNames = (jobName != null && !jobName.isBlank())
                ? List.of(jobName.trim())
                : jobExplorer.getJobNames();

        // Gom TẤT CẢ executions của các jobs, sau đó mới sort và cắt theo limit
        // Tránh tình trạng job đầu tiên chiếm hết limit khiến job thứ hai không xuất hiện
        for (String name : jobNames) {
            List<JobInstance> instances = jobExplorer.getJobInstances(name, 0, 50);
            for (JobInstance instance : instances) {
                List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
                for (JobExecution exec : executions) {
                    list.add(mapToResponse(exec));
                }
            }
        }

        list.sort((a, b) -> Long.compare(b.getJobExecutionId(), a.getJobExecutionId()));

        if (list.size() > limit) {
            list = list.subList(0, limit);
        }

        return ResponseEntity.ok(JobHistoryResponse.builder()
                .totalExecutions(list.size())
                .executions(list)
                .build());
    }

    @Operation(summary = "Khởi động lại (Restart) một JobExecution bị FAILED để chạy tiếp từ vị trí dừng")
    @PostMapping("/executions/{id}/restart")
    public ResponseEntity<JobExecutionResponse> restartJob(@PathVariable Long id) throws Exception {
        UserContext.requireAdmin();

        JobExecution execution = jobExplorer.getJobExecution(id);
        if (execution == null) {
            throw new ResourceNotFoundException("Không tìm thấy JobExecution với ID: " + id);
        }

        if (execution.getStatus() != BatchStatus.FAILED) {
            throw new IllegalArgumentException("Chỉ có thể restart một JobExecution ở trạng thái FAILED. Trạng thái hiện tại: "
                    + execution.getStatus());
        }

        // Đảm bảo cờ lỗi giả lập không còn vũ trang -> restart sẽ chạy tiếp mượt mà từ vị trí dừng!
        demoFailureSwitch.disarm();

        log.info("🔄 [ADMIN RESTART] Đang khởi động lại JobExecution ID: {}", id);
        Long newExecutionId = jobOperator.restart(id);

        JobExecution newExecution = jobExplorer.getJobExecution(newExecutionId);
        return ResponseEntity.ok(mapToResponse(newExecution));
    }

    private JobExecutionResponse mapToResponse(JobExecution execution) {
        List<StepExecutionDto> steps = execution.getStepExecutions().stream()
                .map(step -> StepExecutionDto.builder()
                        .stepName(step.getStepName())
                        .status(step.getStatus().name())
                        .exitCode(step.getExitStatus().getExitCode())
                        .readCount(step.getReadCount())
                        .writeCount(step.getWriteCount())
                        .commitCount(step.getCommitCount())
                        .rollbackCount(step.getRollbackCount())
                        .readSkipCount(step.getReadSkipCount())
                        .processSkipCount(step.getProcessSkipCount())
                        .writeSkipCount(step.getWriteSkipCount())
                        .totalSkipCount(step.getSkipCount())
                        .build())
                .toList();

        return JobExecutionResponse.builder()
                .jobExecutionId(execution.getId())
                .jobName(execution.getJobInstance().getJobName())
                .status(execution.getStatus().name())
                .exitCode(execution.getExitStatus().getExitCode())
                .exitDescription(execution.getExitStatus().getExitDescription())
                .createTime(execution.getCreateTime())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .durationMs(JobExecutionResponse.calculateDuration(execution.getStartTime(), execution.getEndTime()))
                .steps(steps)
                .build();
    }
}
