package com.example.recommender.batch;

import com.example.recommender.batch.job.trending.TrendingPostRedisUpdateListener;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendingPostRedisUpdateListenerTest {

    @Mock
    private RecommenderTrendingPostRepository trendingPostRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TrendingPostRedisUpdateListener listener;

    @Test
    @DisplayName("afterStep: gán rank và lưu Top trending vào Redis cache sau khi step thành công")
    void testAfterStep_ShouldRankAndUpdateRedisCache() throws Exception {
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(stepExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

        RecommenderTrendingPost p1 = RecommenderTrendingPost.builder()
                .id(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .score(0.99)
                .build();

        when(trendingPostRepository.findTopTrending(any(Pageable.class))).thenReturn(List.of(p1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"score\":0.99,\"rank\":1}]");

        ExitStatus exitStatus = listener.afterStep(stepExecution);

        assertEquals(ExitStatus.COMPLETED, exitStatus);
        assertEquals(1, p1.getRank());
        verify(trendingPostRepository, times(1)).saveAll(List.of(p1));
        verify(valueOperations, times(1)).set(eq("trending:posts"), anyString(), any(Duration.class));
    }
}
