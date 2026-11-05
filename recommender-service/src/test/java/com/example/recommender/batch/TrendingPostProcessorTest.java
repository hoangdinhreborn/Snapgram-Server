package com.example.recommender.batch;

import com.example.recommender.batch.job.trending.TrendingPostProcessor;
import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderInteractionViewRepository;
import com.example.recommender.service.RecommendationScoringEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendingPostProcessorTest {

    @Mock
    private RecommenderInteractionViewRepository interactionViewRepository;

    @Mock
    private com.example.recommender.batch.config.DemoFailureSwitch demoFailureSwitch;

    @Spy
    private RecommendationScoringEngine scoringEngine;

    @InjectMocks
    private TrendingPostProcessor processor;

    private RecommenderPostView validPost;

    @BeforeEach
    void setUp() {
        validPost = RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .caption("Hoàng hôn tuyệt đẹp trên sông Sài Gòn")
                .tags("sunset,saigon,photography")
                .visibility("PUBLIC")
                .authorIsPrivate(false)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("Bản ghi hợp lệ: tính toán điểm trending thành công và trả về RecommenderTrendingPost")
    void testProcessValidPost_ShouldReturnTrendingPost() throws Exception {
        when(interactionViewRepository.countLikesByPostId(validPost.getPostId())).thenReturn(150L);
        when(interactionViewRepository.findAvgWatchTimeRatioByPostId(validPost.getPostId())).thenReturn(0.85);

        RecommenderTrendingPost result = processor.process(validPost);

        assertNotNull(result);
        assertEquals(validPost.getPostId(), result.getPostId());
        assertEquals(validPost.getAuthorId(), result.getAuthorId());
        assertEquals(150L, result.getLikesCount());
        assertTrue(result.getScore() > 0.0);
        assertNotNull(result.getCalculatedAt());
    }

    @Test
    @DisplayName("Bản ghi chứa cờ 'DEMO_MALFORMED': ném MalformedPostException để SkipListener bỏ qua")
    void testProcessMalformedPost_ShouldThrowMalformedPostException() {
        RecommenderPostView malformedPost = RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .caption("DEMO_MALFORMED - Dữ liệu rác cố ý tạo để kiểm tra Spring Batch Skip")
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(malformedPost));
    }

    @Test
    @DisplayName("PostId bị null: ném MalformedPostException")
    void testProcessNullPostId_ShouldThrowMalformedPostException() {
        RecommenderPostView nullIdPost = RecommenderPostView.builder()
                .postId(null)
                .authorId(UUID.randomUUID())
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(nullIdPost));
    }

    @Test
    @DisplayName("Mô phỏng sự cố Restart: ném IllegalStateException khi chạm ngưỡng failAtItem và switch armed")
    void testProcessFailAtItem_ShouldThrowIllegalStateExceptionForRestartDemo() {
        processor.setFailAtItem(1);
        when(demoFailureSwitch.tryTrigger()).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> processor.process(validPost));
        assertTrue(ex.getMessage().contains("demo khả năng RESTART"));
    }

    @Test
    @DisplayName("Khi Restart: demoFailureSwitch không trigger thì job chạy qua bình thường không ném lỗi")
    void testProcessFailAtItem_WhenNotTriggered_ShouldPassNormally() throws Exception {
        processor.setFailAtItem(1);
        when(demoFailureSwitch.tryTrigger()).thenReturn(false);
        when(interactionViewRepository.countLikesByPostId(validPost.getPostId())).thenReturn(10L);

        RecommenderTrendingPost result = processor.process(validPost);

        assertNotNull(result);
        assertEquals(validPost.getPostId(), result.getPostId());
    }
}
