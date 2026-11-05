package com.example.recommender.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
public class PrettyBatchJobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("""
                
                ================================================================================
                🚀 [SPRING BATCH START] Job: {} (ExecutionId: {})
                ▶ Start Time: {}
                ▶ Parameters: {}
                ================================================================================
                """,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStartTime() != null ? jobExecution.getStartTime() : LocalDateTime.now(),
                jobExecution.getJobParameters()
        );
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long totalRead = 0;
        long totalWrite = 0;
        long totalSkip = 0;
        long totalCommit = 0;

        for (StepExecution step : jobExecution.getStepExecutions()) {
            totalRead += step.getReadCount();
            totalWrite += step.getWriteCount();
            totalSkip += step.getSkipCount();
            totalCommit += step.getCommitCount();
        }

        Duration duration = Duration.ZERO;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
        }

        String icon = jobExecution.getStatus() == BatchStatus.COMPLETED ? "✅" : "❌";

        log.info("""
                
                ================================================================================
                {} [SPRING BATCH END] Job: {} (ExecutionId: {})
                ▶ Status: {} | ExitCode: {}
                ▶ Duration: {} ms ({} sec)
                ▶ Metrics: Read={}, Written={}, Skipped={}, Commits={}
                ================================================================================
                """,
                icon,
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                duration.toMillis(),
                String.format("%.2f", duration.toMillis() / 1000.0),
                totalRead, totalWrite, totalSkip, totalCommit
        );
    }
}
