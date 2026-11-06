package com.example.recommender.batch;

import com.example.recommender.batch.job.trending.TrendingPostWriter;
import com.example.recommender.batch.job.trending.TrendingPostsJobConfig;
import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.repository.RecommenderPostViewRepository;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import com.example.recommender.service.RecommendationScoringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class TrendingPostsJobIntegrationTest {

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher asyncJobLauncher;

    @Autowired
    @Qualifier(TrendingPostsJobConfig.JOB_NAME)
    private Job trendingPostsJob;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private RecommenderPostViewRepository postViewRepository;

    @Autowired
    private RecommenderTrendingPostRepository trendingPostRepository;

    @MockitoSpyBean
    private RecommendationScoringEngine scoringEngine;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        trendingPostRepository.deleteAll();
        postViewRepository.deleteAll();
    }

    private JobExecution waitForJob(JobExecution execution, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (execution.isRunning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            execution = jobExplorer.getJobExecution(execution.getId());
        }
        return execution;
    }

    @Test
    @DisplayName("Integration Test: trendingPostsJob chạy thành công COMPLETED, ghi đúng số dòng và idempotent khi chạy lần 2")
    void testTrendingPostsJob_Completed_And_Idempotent() throws Exception {
        // Tạo 10 bài viết hợp lệ
        List<RecommenderPostView> posts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            posts.add(RecommenderPostView.builder()
                    .postId(UUID.randomUUID())
                    .authorId(UUID.randomUUID())
                    .contentType("POST")
                    .caption("Bài viết trending #" + i)
                    .visibility("PUBLIC")
                    .authorIsPrivate(false)
                    .createdAt(Instant.now().minusSeconds(i * 100))
                    .build());
        }
        postViewRepository.saveAll(posts);

        // Chạy lần 1
        JobParameters params1 = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("runId", "run-1")
                .toJobParameters();

        JobExecution execution1 = asyncJobLauncher.run(trendingPostsJob, params1);
        execution1 = waitForJob(execution1, 5000);

        assertEquals(BatchStatus.COMPLETED, execution1.getStatus());
        assertEquals(10, trendingPostRepository.count());

        // Chạy lần 2 (idempotent, không tăng số dòng)
        JobParameters params2 = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("runId", "run-2")
                .toJobParameters();

        JobExecution execution2 = asyncJobLauncher.run(trendingPostsJob, params2);
        execution2 = waitForJob(execution2, 5000);

        assertEquals(BatchStatus.COMPLETED, execution2.getStatus());
        assertEquals(10, trendingPostRepository.count());
    }

    @Test
    @DisplayName("Integration Test Skip: có bản ghi hỏng nằm trong dữ liệu, job COMPLETED và skipCount đúng")
    void testTrendingPostsJob_SkipMalformedRecord() throws Exception {
        // Tạo 5 bài viết hợp lệ + 1 bài viết hỏng (contentType không hợp lệ)
        List<RecommenderPostView> posts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            posts.add(RecommenderPostView.builder()
                    .postId(UUID.randomUUID())
                    .authorId(UUID.randomUUID())
                    .contentType("IMAGE")
                    .caption("Bài viết hợp lệ #" + i)
                    .visibility("PUBLIC")
                    .authorIsPrivate(false)
                    .createdAt(Instant.now().minusSeconds(i * 100))
                    .build());
        }
        // Bản ghi hỏng (contentType không hợp lệ, độ dài <= 20 chars)
        posts.add(RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("CORRUPTED")
                .caption("Bản ghi hỏng")
                .visibility("PUBLIC")
                .authorIsPrivate(false)
                .createdAt(Instant.now())
                .build());

        postViewRepository.saveAll(posts);

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("runId", "skip-test")
                .toJobParameters();

        JobExecution execution = asyncJobLauncher.run(trendingPostsJob, params);
        execution = waitForJob(execution, 5000);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(5, trendingPostRepository.count());

        long totalSkipCount = execution.getStepExecutions().stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum();
        assertEquals(1, totalSkipCount);
    }

    @Test
    @DisplayName("Integration Test Restart: ném lỗi 1 lần -> execution FAILED; restart -> COMPLETED, không đọc lại chunk cũ")
    void testTrendingPostsJob_Restart_After_Failure() throws Exception {
        // Tạo 10 bài viết (chunk-size = 5 trong application-test.yml -> gồm 2 chunks)
        List<RecommenderPostView> posts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            posts.add(RecommenderPostView.builder()
                    .postId(UUID.randomUUID())
                    .authorId(UUID.randomUUID())
                    .contentType("IMAGE")
                    .caption("Bài viết restart #" + i)
                    .visibility("PUBLIC")
                    .authorIsPrivate(false)
                    .createdAt(Instant.now().minusSeconds(i * 100))
                    .build());
        }
        postViewRepository.saveAll(posts);

        // Giả lập lỗi đúng 1 lần ở item #6 (bắt đầu chunk 2):
        AtomicBoolean shouldFail = new AtomicBoolean(true);
        AtomicInteger processedItems = new AtomicInteger(0);
        doAnswer(invocation -> {
            int count = processedItems.incrementAndGet();
            if (shouldFail.get() && count == 6) {
                shouldFail.set(false); // Chỉ ném lỗi 1 lần duy nhất
                throw new IllegalStateException("Simulated transient scoring failure for restart testing");
            }
            return invocation.callRealMethod();
        }).when(scoringEngine).calculateExploreScore(any(), any(), anyLong(), any());

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("runId", "restart-test")
                .toJobParameters();

        JobExecution failedExecution = asyncJobLauncher.run(trendingPostsJob, params);
        failedExecution = waitForJob(failedExecution, 5000);

        assertEquals(BatchStatus.FAILED, failedExecution.getStatus());
        // Chunk 1 đã commit thành công 5 bài
        assertEquals(5, trendingPostRepository.count());

        // Restart execution bị FAILED
        Long newExecutionId = jobOperator.restart(failedExecution.getId());
        assertNotNull(newExecutionId);

        JobExecution restartedExecution = jobExplorer.getJobExecution(newExecutionId);
        restartedExecution = waitForJob(restartedExecution, 5000);

        assertEquals(BatchStatus.COMPLETED, restartedExecution.getStatus());
        // Toàn bộ 10 bài viết đã được ghi xong
        assertEquals(10, trendingPostRepository.count());

        // Kiểm tra reader không đọc lại chunk 1: readCount của lần restart chỉ là 5 (nhỏ hơn tổng 10)
        long restartReadCount = restartedExecution.getStepExecutions().stream()
                .mapToLong(StepExecution::getReadCount)
                .sum();
        assertEquals(5, restartReadCount);
    }
}
