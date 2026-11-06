package com.example.recommender.service;

import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
import com.example.recommender.exception.JobConflictException;
import com.example.recommender.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchJobServiceTest {

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private JobLauncher asyncJobLauncher;

    private BatchJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BatchJobServiceImpl(
                jobExplorer,
                jobOperator,
                applicationContext,
                asyncJobLauncher
        );
    }

    @Test
    @DisplayName("runJob: khởi chạy job hợp lệ trả về JobExecutionResponse")
    void testRunJob_Success() throws Exception {
        Job mockJob = mock(Job.class);
        when(applicationContext.containsBean("trendingPostsJob")).thenReturn(true);
        when(applicationContext.getBean("trendingPostsJob")).thenReturn(mockJob);
        when(jobExplorer.findRunningJobExecutions("trendingPostsJob")).thenReturn(Collections.emptySet());

        JobInstance jobInstance = new JobInstance(10L, "trendingPostsJob");
        JobExecution jobExecution = new JobExecution(100L, jobInstance, new JobParameters());
        jobExecution.setStatus(BatchStatus.STARTED);
        jobExecution.setCreateTime(LocalDateTime.now());
        jobExecution.setStartTime(LocalDateTime.now());
        jobExecution.setExitStatus(ExitStatus.UNKNOWN);

        when(asyncJobLauncher.run(eq(mockJob), any(JobParameters.class))).thenReturn(jobExecution);

        JobExecutionResponse response = service.runJob("trendingPostsJob");

        assertNotNull(response);
        assertEquals(100L, response.getJobExecutionId());
        assertEquals("STARTED", response.getStatus());
    }

    @Test
    @DisplayName("runJob với job không tồn tại: ném ResourceNotFoundException")
    void testRunJob_NotFound() {
        when(applicationContext.containsBean("nonExistentJob")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.runJob("nonExistentJob"));
    }

    @Test
    @DisplayName("runJob với bean không phải Job: ném IllegalArgumentException")
    void testRunJob_NotAJob() {
        when(applicationContext.containsBean("someBean")).thenReturn(true);
        when(applicationContext.getBean("someBean")).thenReturn("A String Object");
        assertThrows(IllegalArgumentException.class, () -> service.runJob("someBean"));
    }

    @Test
    @DisplayName("runJob khi job đang có execution RUNNING: ném JobConflictException")
    void testRunJob_Conflict() {
        Job mockJob = mock(Job.class);
        when(applicationContext.containsBean("trendingPostsJob")).thenReturn(true);
        when(applicationContext.getBean("trendingPostsJob")).thenReturn(mockJob);

        JobExecution runningExecution = mock(JobExecution.class);
        when(runningExecution.getId()).thenReturn(99L);
        when(jobExplorer.findRunningJobExecutions("trendingPostsJob")).thenReturn(Set.of(runningExecution));

        assertThrows(JobConflictException.class, () -> service.runJob("trendingPostsJob"));
    }

    @Test
    @DisplayName("getExecution với ID hợp lệ: trả về response")
    void testGetExecution_Success() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution execution = new JobExecution(55L, jobInstance, new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);
        execution.setExitStatus(ExitStatus.COMPLETED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(execution);

        JobExecutionResponse response = service.getExecution(55L);
        assertNotNull(response);
        assertEquals(55L, response.getJobExecutionId());
    }

    @Test
    @DisplayName("getExecution với ID không tồn tại: ném ResourceNotFoundException")
    void testGetExecution_NotFound() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);
        assertThrows(ResourceNotFoundException.class, () -> service.getExecution(999L));
    }

    @Test
    @DisplayName("getExecutionHistory: gom tất cả execution của các job và sort theo ID giảm dần")
    void testGetExecutionHistory_SortAndLimit() {
        JobInstance inst1 = new JobInstance(1L, "trendingPostsJob");
        JobExecution exec1 = new JobExecution(10L, inst1, new JobParameters());
        exec1.setStatus(BatchStatus.COMPLETED);
        exec1.setExitStatus(ExitStatus.COMPLETED);

        JobInstance inst2 = new JobInstance(2L, "userRecommendationJob");
        JobExecution exec2 = new JobExecution(20L, inst2, new JobParameters());
        exec2.setStatus(BatchStatus.COMPLETED);
        exec2.setExitStatus(ExitStatus.COMPLETED);

        when(jobExplorer.getJobInstances("trendingPostsJob", 0, 10)).thenReturn(List.of(inst1));
        when(jobExplorer.getJobExecutions(inst1)).thenReturn(List.of(exec1));

        when(jobExplorer.getJobInstances("userRecommendationJob", 0, 10)).thenReturn(List.of(inst2));
        when(jobExplorer.getJobExecutions(inst2)).thenReturn(List.of(exec2));

        JobHistoryResponse history = service.getExecutionHistory(10);

        assertEquals(2, history.getTotalExecutions());
        // ID 20 phải đứng trước ID 10
        assertEquals(20L, history.getExecutions().get(0).getJobExecutionId());
        assertEquals(10L, history.getExecutions().get(1).getJobExecutionId());
    }

    @Test
    @DisplayName("restartJob: execution FAILED hợp lệ restart thành công")
    void testRestartJob_Success() throws Exception {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution failedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        failedExecution.setStatus(BatchStatus.FAILED);
        failedExecution.setExitStatus(ExitStatus.FAILED);

        JobExecution newExecution = new JobExecution(56L, jobInstance, new JobParameters());
        newExecution.setStatus(BatchStatus.STARTED);
        newExecution.setExitStatus(ExitStatus.UNKNOWN);

        when(jobExplorer.getJobExecution(55L)).thenReturn(failedExecution);
        when(jobExplorer.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution));
        when(jobOperator.restart(55L)).thenReturn(56L);
        when(jobExplorer.getJobExecution(56L)).thenReturn(newExecution);

        JobExecutionResponse response = service.restartJob(55L);

        assertEquals(56L, response.getJobExecutionId());
    }

    @Test
    @DisplayName("restartJob khi execution không ở trạng thái FAILED: ném IllegalArgumentException")
    void testRestartJob_NotFailed_ThrowsIllegalArgument() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution completedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        completedExecution.setStatus(BatchStatus.COMPLETED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(completedExecution);

        assertThrows(IllegalArgumentException.class, () -> service.restartJob(55L));
    }

    @Test
    @DisplayName("restartJob khi instance đã có execution COMPLETED: ném JobConflictException")
    void testRestartJob_InstanceAlreadyCompleted_ThrowsConflict() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution failedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        failedExecution.setStatus(BatchStatus.FAILED);

        JobExecution completedExecution = new JobExecution(56L, jobInstance, new JobParameters());
        completedExecution.setStatus(BatchStatus.COMPLETED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(failedExecution);
        when(jobExplorer.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution, completedExecution));

        assertThrows(JobConflictException.class, () -> service.restartJob(55L));
    }
}
