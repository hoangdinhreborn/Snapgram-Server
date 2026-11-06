package com.example.recommender.controller;

import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
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
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/batch")
@Slf4j
@Tag(name = "Admin Batch API", description = "Các API điều khiển và giám sát Spring Batch (Admin Only)")
public class AdminBatchController {

    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final ApplicationContext applicationContext;
    private final JobLauncher asyncJobLauncher;

    @Autowired
    public AdminBatchController(
            JobExplorer jobExplorer,
            JobOperator jobOperator,
            ApplicationContext applicationContext,
            @Qualifier("asyncJobLauncher") JobLauncher asyncJobLauncher
    ) {
        this.jobExplorer = jobExplorer;
        this.jobOperator = jobOperator;
        this.applicationContext = applicationContext;
        this.asyncJobLauncher = asyncJobLauncher;
    }

    @Operation(summary = "Kích hoạt thủ công một Spring Batch Job")
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<JobExecutionResponse> runJob(
            @PathVariable String jobName) throws Exception {
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

        // 3. Khởi tạo JobParameters
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("triggerSource", "ADMIN_API")
                .toJobParameters();

        log.info("admin_manual_run job={} params={}", jobName, params);

        JobExecution execution = asyncJobLauncher.run(job, params);
        return ResponseEntity.ok(mapToResponse(execution));
    }

    @Operation(summary = "Xem trạng thái & số liệu chi tiết của một lần chạy (Execution ID)")
    @GetMapping("/executions/{id}")
    public ResponseEntity<JobExecutionResponse> getExecution(@PathVariable Long id) {
        UserContext.requireAdmin();
        JobExecution execution = findJobExecutionOrThrow(id);
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

        JobExecution execution = findJobExecutionOrThrow(id);

        if (execution.getStatus() != BatchStatus.FAILED) {
            throw new IllegalArgumentException("Chỉ có thể restart một JobExecution ở trạng thái FAILED. Trạng thái hiện tại: "
                    + execution.getStatus());
        }

        JobInstance instance = execution.getJobInstance();
        if (instance != null) {
            List<JobExecution> instanceExecutions = jobExplorer.getJobExecutions(instance);

            // 1. Kiểm tra xem JobInstance đã có execution COMPLETED nào chưa
            Optional<JobExecution> completedExec = instanceExecutions.stream()
                    .filter(e -> e.getStatus() == BatchStatus.COMPLETED)
                    .findFirst();
            if (completedExec.isPresent()) {
                throw new JobConflictException("JobInstance #" + instance.getInstanceId()
                        + " (thuộc JobExecution #" + id + ") đã hoàn thành thành công tại JobExecution #"
                        + completedExec.get().getId() + ". Không thể restart lại.");
            }

            // 2. Kiểm tra xem JobInstance có execution nào đang chạy (STARTING/STARTED) không
            Optional<JobExecution> runningExec = instanceExecutions.stream()
                    .filter(e -> e.getStatus() == BatchStatus.STARTING || e.getStatus() == BatchStatus.STARTED)
                    .findFirst();
            if (runningExec.isPresent()) {
                throw new JobConflictException("JobInstance #" + instance.getInstanceId()
                        + " hiện đang có JobExecution #" + runningExec.get().getId()
                        + " đang chạy (trạng thái: " + runningExec.get().getStatus() + "). Vui lòng đợi hoàn tất trước khi restart.");
            }
        }

        log.info("admin_restart executionId={}", id);
        Long newExecutionId = jobOperator.restart(id);

        JobExecution newExecution = findJobExecutionOrThrow(newExecutionId);
        return ResponseEntity.ok(mapToResponse(newExecution));
    }

    private JobExecution findJobExecutionOrThrow(Long id) {
        JobExecution execution;
        try {
            execution = jobExplorer.getJobExecution(id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            execution = null;
        }
        if (execution == null) {
            throw new ResourceNotFoundException("Không tìm thấy JobExecution với ID: " + id);
        }
        return execution;
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
