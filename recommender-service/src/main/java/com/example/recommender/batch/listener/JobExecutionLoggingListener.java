package com.example.recommender.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
public class JobExecutionLoggingListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("job_start job={} executionId={} params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getJobParameters());
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

        long durationMs = 0;
        if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
            durationMs = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        }

        log.info("job_end job={} executionId={} status={} exitCode={} readCount={} writeCount={} skipCount={} commitCount={} durationMs={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                totalRead,
                totalWrite,
                totalSkip,
                totalCommit,
                durationMs);
    }
}
