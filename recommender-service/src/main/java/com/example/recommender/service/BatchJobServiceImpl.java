package com.example.recommender.service;

import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
import com.example.recommender.dto.batch.StepExecutionDto;
import com.example.recommender.exception.JobConflictException;
import com.example.recommender.exception.ResourceNotFoundException;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class BatchJobServiceImpl implements BatchJobService {

    private final JobExplorer jobExplorer;
    private final JobOperator jobOperator;
    private final ApplicationContext applicationContext;
    private final JobLauncher asyncJobLauncher;

    public BatchJobServiceImpl(
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

    @Override
    public JobExecutionResponse runJob(String jobName) throws Exception {
        // 1. Kiểm tra Job Bean có tồn tại trong context không
        if (!applicationContext.containsBean(jobName)) {
            throw new ResourceNotFoundException("Không tìm thấy Job có tên '" + jobName + "'");
        }
        Object bean = applicationContext.getBean(jobName);
        if (!(bean instanceof Job job)) {
            throw new IllegalArgumentException("Bean '" + jobName + "' không phải là một Spring Batch Job");
        }

        // 2. Chống chạy trùng Job (Concurrency Protection)
        // Lưu ý giới hạn: Cơ chế findRunningJobExecutions này an toàn cho cấu hình Single-Instance.
        // Khi scale đa instance trong môi trường production, cần sử dụng Distributed Lock (như ShedLock hoặc Redis lock).
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

        log.info("job_launch_requested job={} params={}", jobName, params);

        JobExecution execution = asyncJobLauncher.run(job, params);
        return mapToResponse(execution);
    }

    @Override
    public JobExecutionResponse getExecution(Long id) {
        JobExecution execution = findJobExecutionOrThrow(id);
        return mapToResponse(execution);
    }

    @Override
    public JobHistoryResponse getExecutionHistory(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        List<JobExecutionResponse> allExecutions = new ArrayList<>();
        List<String> jobNames = List.of("trendingPostsJob", "userRecommendationJob");

        for (String jobName : jobNames) {
            try {
                List<JobInstance> instances = jobExplorer.getJobInstances(jobName, 0, safeLimit);
                for (JobInstance instance : instances) {
                    List<JobExecution> executions = jobExplorer.getJobExecutions(instance);
                    for (JobExecution execution : executions) {
                        allExecutions.add(mapToResponse(execution));
                    }
                }
            } catch (Exception e) {
                log.warn("get_execution_history_error job={} reason={}", jobName, e.getMessage());
            }
        }

        // Gom tất cả execution của các job, sort theo ID giảm dần rồi mới cắt theo limit
        allExecutions.sort((a, b) -> Long.compare(b.getJobExecutionId(), a.getJobExecutionId()));
        List<JobExecutionResponse> trimmed = allExecutions.size() > safeLimit
                ? allExecutions.subList(0, safeLimit)
                : allExecutions;

        return JobHistoryResponse.builder()
                .totalExecutions(trimmed.size())
                .executions(trimmed)
                .build();
    }

    @Override
    public JobExecutionResponse restartJob(Long id) throws Exception {
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

        log.info("job_restart_requested executionId={}", id);
        Long newExecutionId = jobOperator.restart(id);

        JobExecution newExecution = findJobExecutionOrThrow(newExecutionId);
        return mapToResponse(newExecution);
    }

    @Override
    public JobExecutionResponse stopJob(Long id) throws Exception {
        JobExecution execution = findJobExecutionOrThrow(id);
        if (!execution.isRunning()) {
            throw new JobConflictException("JobExecution #" + id + " không ở trạng thái đang chạy. Trạng thái: " + execution.getStatus());
        }

        log.info("job_stop_requested executionId={}", id);
        jobOperator.stop(id);

        JobExecution updated = findJobExecutionOrThrow(id);
        return mapToResponse(updated);
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
