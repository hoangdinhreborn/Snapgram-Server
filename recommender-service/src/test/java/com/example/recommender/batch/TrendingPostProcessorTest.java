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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendingPostProcessorTest {

    @Mock
    private RecommenderInteractionViewRepository interactionViewRepository;

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
    @DisplayName("Bản ghi có avgWatchTime null: xử lý null-safe không gây lỗi")
    void testProcessValidPost_NullAvgWatchTime_ShouldHandleGracefully() throws Exception {
        when(interactionViewRepository.countLikesByPostId(validPost.getPostId())).thenReturn(50L);
        when(interactionViewRepository.findAvgWatchTimeRatioByPostId(validPost.getPostId())).thenReturn(null);

        RecommenderTrendingPost result = processor.process(validPost);

        assertNotNull(result);
        assertEquals(validPost.getPostId(), result.getPostId());
        assertEquals(50L, result.getLikesCount());
        assertTrue(result.getScore() > 0.0);
    }

    @Test
    @DisplayName("PostId bị null: ném MalformedPostException")
    void testProcessNullPostId_ShouldThrowMalformedPostException() {
        RecommenderPostView nullIdPost = RecommenderPostView.builder()
                .postId(null)
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .createdAt(Instant.now())
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(nullIdPost));
    }

    @Test
    @DisplayName("AuthorId bị null: ném MalformedPostException")
    void testProcessNullAuthorId_ShouldThrowMalformedPostException() {
        RecommenderPostView nullAuthorPost = RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(null)
                .contentType("IMAGE")
                .createdAt(Instant.now())
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(nullAuthorPost));
    }

    @Test
    @DisplayName("CreatedAt bị null: ném MalformedPostException")
    void testProcessNullCreatedAt_ShouldThrowMalformedPostException() {
        RecommenderPostView nullCreatedPost = RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .createdAt(null)
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(nullCreatedPost));
    }

    @Test
    @DisplayName("ContentType không hợp lệ: ném MalformedPostException")
    void testProcessInvalidContentType_ShouldThrowMalformedPostException() {
        RecommenderPostView invalidTypePost = RecommenderPostView.builder()
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("UNSUPPORTED_TYPE_XYZ")
                .createdAt(Instant.now())
                .visibility("PUBLIC")
                .build();

        assertThrows(MalformedPostException.class, () -> processor.process(invalidTypePost));
    }

    @Test
    @DisplayName("Thời gian calculatedAt khớp với timestamp trong jobParameters")
    void testProcess_TimestampMatchesJobParameters() throws Exception {
        long fixedTimestamp = 1730000000000L;
        processor.setRunTimestamp(fixedTimestamp);
        when(interactionViewRepository.countLikesByPostId(validPost.getPostId())).thenReturn(10L);

        RecommenderTrendingPost result = processor.process(validPost);

        assertEquals(Instant.ofEpochMilli(fixedTimestamp), result.getCalculatedAt());
    }
}
