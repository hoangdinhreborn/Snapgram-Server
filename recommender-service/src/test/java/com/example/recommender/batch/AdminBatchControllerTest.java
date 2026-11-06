package com.example.recommender.batch;

import com.example.recommender.batch.service.DemoDataSeederService;
import com.example.recommender.controller.AdminBatchController;
import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.SeedDataResponse;
import com.example.recommender.exception.AccessDeniedException;
import com.example.recommender.exception.JobConflictException;
import com.example.recommender.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBatchControllerTest {

    @Mock
    private JobExplorer jobExplorer;

    @Mock
    private JobOperator jobOperator;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private DemoDataSeederService demoDataSeederService;

    @Mock
    private JobLauncher asyncJobLauncher;

    @Mock
    private com.example.recommender.batch.config.DemoFailureSwitch demoFailureSwitch;

    private AdminBatchController controller;

    private MockedStatic<UserContext> mockedUserContext;

    @BeforeEach
    void setUp() {
        mockedUserContext = mockStatic(UserContext.class);
        controller = new AdminBatchController(
                jobExplorer,
                jobOperator,
                applicationContext,
                demoDataSeederService,
                asyncJobLauncher,
                demoFailureSwitch
        );
    }

    @AfterEach
    void tearDown() {
        mockedUserContext.close();
    }

    @Test
    @DisplayName("Admin gọi seedDemoData: thành công trả về SeedDataResponse")
    void testSeedDemoData_Success() {
        SeedDataResponse seedResp = SeedDataResponse.builder()
                .postsCreated(100)
                .interactionsCreated(200)
                .followsCreated(50)
                .malformedPostsCreated(3)
                .message("OK")
                .build();

        when(demoDataSeederService.seedDemoData(100)).thenReturn(seedResp);

        ResponseEntity<SeedDataResponse> response = controller.seedDemoData(100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100, response.getBody().getPostsCreated());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
    }

    @Test
    @DisplayName("Admin kích hoạt runJob: trả về JobExecutionResponse với ID mới")
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

        ResponseEntity<JobExecutionResponse> response = controller.runJob("trendingPostsJob", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getJobExecutionId());
        assertEquals("STARTED", response.getBody().getStatus());
    }

    @Test
    @DisplayName("Chống chạy trùng: ném JobConflictException khi job đang có instance RUNNING")
    void testRunJob_JobConflict() {
        Job mockJob = mock(Job.class);
        when(applicationContext.containsBean("trendingPostsJob")).thenReturn(true);
        when(applicationContext.getBean("trendingPostsJob")).thenReturn(mockJob);

        JobExecution runningExecution = mock(JobExecution.class);
        when(runningExecution.getId()).thenReturn(99L);
        when(jobExplorer.findRunningJobExecutions("trendingPostsJob")).thenReturn(Set.of(runningExecution));

        assertThrows(JobConflictException.class, () -> controller.runJob("trendingPostsJob", null));
    }

    @Test
    @DisplayName("Restart Job: restart execution hợp lệ trả execution mới")
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

        ResponseEntity<JobExecutionResponse> response = controller.restartJob(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(56L, response.getBody().getJobExecutionId());
    }

    @Test
    @DisplayName("Restart Job lần hai trên cùng execution đã hoàn thành: ném JobConflictException (HTTP 409)")
    void testRestartJob_SecondRestart_ThrowsConflict() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution failedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        failedExecution.setStatus(BatchStatus.FAILED);

        JobExecution completedExecution = new JobExecution(56L, jobInstance, new JobParameters());
        completedExecution.setStatus(BatchStatus.COMPLETED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(failedExecution);
        when(jobExplorer.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution, completedExecution));

        JobConflictException ex = assertThrows(JobConflictException.class, () -> controller.restartJob(55L));
        assertTrue(ex.getMessage().contains("đã hoàn thành thành công tại JobExecution #56"));
    }

    @Test
    @DisplayName("Restart Job khi instance đang có execution chạy: ném JobConflictException (HTTP 409)")
    void testRestartJob_InstanceRunning_ThrowsConflict() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution failedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        failedExecution.setStatus(BatchStatus.FAILED);

        JobExecution runningExecution = new JobExecution(56L, jobInstance, new JobParameters());
        runningExecution.setStatus(BatchStatus.STARTED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(failedExecution);
        when(jobExplorer.getJobExecutions(jobInstance)).thenReturn(List.of(failedExecution, runningExecution));

        JobConflictException ex = assertThrows(JobConflictException.class, () -> controller.restartJob(55L));
        assertTrue(ex.getMessage().contains("hiện đang có JobExecution #56 đang chạy"));
    }

    @Test
    @DisplayName("Restart Job với ID không tồn tại: ném ResourceNotFoundException (HTTP 404)")
    void testRestartJob_NotFound_ThrowsNotFound() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);
        assertThrows(com.example.recommender.exception.ResourceNotFoundException.class,
                () -> controller.restartJob(999L));
    }

    @Test
    @DisplayName("Restart Job khi execution không ở trạng thái FAILED: ném IllegalArgumentException (HTTP 400)")
    void testRestartJob_NotFailed_ThrowsBadRequest() {
        JobInstance jobInstance = new JobInstance(1L, "trendingPostsJob");
        JobExecution completedExecution = new JobExecution(55L, jobInstance, new JobParameters());
        completedExecution.setStatus(BatchStatus.COMPLETED);

        when(jobExplorer.getJobExecution(55L)).thenReturn(completedExecution);

        assertThrows(IllegalArgumentException.class, () -> controller.restartJob(55L));
    }

    @Test
    @DisplayName("runJob với tên Job không tồn tại: ném ResourceNotFoundException (HTTP 404)")
    void testRunJob_NotFound() {
        when(applicationContext.containsBean("nonExistentJob")).thenReturn(false);
        assertThrows(com.example.recommender.exception.ResourceNotFoundException.class,
                () -> controller.runJob("nonExistentJob", null));
    }

    @Test
    @DisplayName("getExecution với ID không tồn tại: ném ResourceNotFoundException (HTTP 404)")
    void testGetExecution_NotFound() {
        when(jobExplorer.getJobExecution(999L)).thenReturn(null);
        assertThrows(com.example.recommender.exception.ResourceNotFoundException.class,
                () -> controller.getExecution(999L));
    }

    @Test
    @DisplayName("runJob với failAtItem: vũ trang DemoFailureSwitch")
    void testRunJob_WithFailAtItem_ArmsSwitch() throws Exception {
        Job mockJob = mock(Job.class);
        when(applicationContext.containsBean("trendingPostsJob")).thenReturn(true);
        when(applicationContext.getBean("trendingPostsJob")).thenReturn(mockJob);
        when(jobExplorer.findRunningJobExecutions("trendingPostsJob")).thenReturn(Collections.emptySet());

        JobInstance jobInstance = new JobInstance(10L, "trendingPostsJob");
        JobExecution jobExecution = new JobExecution(101L, jobInstance, new JobParameters());
        jobExecution.setStatus(BatchStatus.STARTED);
        jobExecution.setCreateTime(LocalDateTime.now());
        jobExecution.setStartTime(LocalDateTime.now());
        jobExecution.setExitStatus(ExitStatus.UNKNOWN);

        when(asyncJobLauncher.run(eq(mockJob), any(JobParameters.class))).thenReturn(jobExecution);

        ResponseEntity<JobExecutionResponse> response = controller.runJob("trendingPostsJob", 45);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(101L, response.getBody().getJobExecutionId());
        verify(demoFailureSwitch, times(1)).arm();
    }
}
