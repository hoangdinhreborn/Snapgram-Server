package com.example.recommender.batch;

import com.example.recommender.controller.AdminBatchController;
import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;
import com.example.recommender.exception.AccessDeniedException;
import com.example.recommender.service.BatchJobService;
import com.example.recommender.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBatchControllerTest {

    @Mock
    private BatchJobService batchJobService;

    private AdminBatchController controller;

    private MockedStatic<UserContext> mockedUserContext;

    @BeforeEach
    void setUp() {
        mockedUserContext = mockStatic(UserContext.class);
        controller = new AdminBatchController(batchJobService);
    }

    @AfterEach
    void tearDown() {
        mockedUserContext.close();
    }

    @Test
    @DisplayName("Admin kích hoạt runJob: gọi batchJobService và trả về 200 OK")
    void testRunJob_Success() throws Exception {
        JobExecutionResponse mockResp = JobExecutionResponse.builder()
                .jobExecutionId(100L)
                .jobName("trendingPostsJob")
                .status("STARTED")
                .build();
        when(batchJobService.runJob("trendingPostsJob")).thenReturn(mockResp);

        ResponseEntity<JobExecutionResponse> response = controller.runJob("trendingPostsJob");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100L, response.getBody().getJobExecutionId());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
        verify(batchJobService, times(1)).runJob("trendingPostsJob");
    }

    @Test
    @DisplayName("User không có quyền Admin gọi runJob: ném AccessDeniedException (HTTP 403)")
    void testRunJob_NotAdmin_ThrowsAccessDenied() {
        mockedUserContext.when(UserContext::requireAdmin).thenThrow(new AccessDeniedException("Access denied: ADMIN role required"));

        assertThrows(AccessDeniedException.class, () -> controller.runJob("trendingPostsJob"));
        verifyNoInteractions(batchJobService);
    }

    @Test
    @DisplayName("Admin xem execution: gọi batchJobService và trả về kết quả")
    void testGetExecution_Success() {
        JobExecutionResponse mockResp = JobExecutionResponse.builder()
                .jobExecutionId(55L)
                .jobName("trendingPostsJob")
                .status("COMPLETED")
                .build();
        when(batchJobService.getExecution(55L)).thenReturn(mockResp);

        ResponseEntity<JobExecutionResponse> response = controller.getExecution(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(55L, response.getBody().getJobExecutionId());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
    }

    @Test
    @DisplayName("Admin xem lịch sử: gọi batchJobService và trả về JobHistoryResponse")
    void testGetExecutionHistory_Success() {
        JobHistoryResponse mockHistory = JobHistoryResponse.builder()
                .totalExecutions(1)
                .executions(List.of(JobExecutionResponse.builder().jobExecutionId(10L).build()))
                .build();
        when(batchJobService.getExecutionHistory(10)).thenReturn(mockHistory);

        ResponseEntity<JobHistoryResponse> response = controller.getExecutionHistory(10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalExecutions());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
    }

    @Test
    @DisplayName("Admin restart execution: gọi batchJobService và trả về execution mới")
    void testRestartJob_Success() throws Exception {
        JobExecutionResponse restartResp = JobExecutionResponse.builder()
                .jobExecutionId(56L)
                .status("STARTED")
                .build();
        when(batchJobService.restartJob(55L)).thenReturn(restartResp);

        ResponseEntity<JobExecutionResponse> response = controller.restartJob(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(56L, response.getBody().getJobExecutionId());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
    }

    @Test
    @DisplayName("Admin stop execution: gọi batchJobService và trả về kết quả")
    void testStopJob_Success() throws Exception {
        JobExecutionResponse stopResp = JobExecutionResponse.builder()
                .jobExecutionId(55L)
                .status("STOPPED")
                .build();
        when(batchJobService.stopJob(55L)).thenReturn(stopResp);

        ResponseEntity<JobExecutionResponse> response = controller.stopJob(55L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("STOPPED", response.getBody().getStatus());
        mockedUserContext.verify(UserContext::requireAdmin, times(1));
    }
}
