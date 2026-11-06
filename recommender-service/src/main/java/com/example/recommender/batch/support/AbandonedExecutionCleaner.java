package com.example.recommender.batch.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Tự động phục hồi các JobExecution/StepExecution mồ côi (orphaned) khi service khởi động.
 * Nếu lần chạy trước bị tắt đột ngột (crash, container restart, OOM), các execution đang ở trạng
 * thái STARTING/STARTED sẽ bị kẹt vĩnh viễn và khiến API trả về HTTP 409 Conflict.
 * Component này quét và đánh dấu FAILED các execution mồ côi đó lúc startup để cho phép restart an toàn.
 *
 * Giới hạn kiến trúc: Cơ chế quét lúc startup này an toàn cho mô hình Single-Instance.
 * Khi scale đa instance đồng thời, cần sử dụng Distributed Lock (như ShedLock hoặc Redis lock)
 * để tránh việc instance mới khởi động đánh dấu nhầm execution đang chạy thực sự của instance khác.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AbandonedExecutionCleaner implements ApplicationRunner {

    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (String jobName : jobExplorer.getJobNames()) {
                Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
                for (JobExecution execution : runningExecutions) {
                    log.warn("abandoned_execution_detected id={} job={} oldStatus={} newStatus=FAILED",
                            execution.getId(), jobName, execution.getStatus());

                    execution.setStatus(BatchStatus.FAILED);
                    execution.setEndTime(LocalDateTime.now());
                    execution.setExitStatus(new ExitStatus("FAILED", "Tiến trình bị gián đoạn đột ngột (Abandoned on startup)"));
                    jobRepository.update(execution);

                    for (StepExecution step : execution.getStepExecutions()) {
                        if (step.getStatus() == BatchStatus.STARTED || step.getStatus() == BatchStatus.STARTING) {
                            step.setStatus(BatchStatus.FAILED);
                            step.setEndTime(LocalDateTime.now());
                            step.setExitStatus(new ExitStatus("FAILED", "Step bị gián đoạn đột ngột"));
                            jobRepository.update(step);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("abandoned_execution_cleaner_error message={}", e.getMessage(), e);
        }
    }
}
