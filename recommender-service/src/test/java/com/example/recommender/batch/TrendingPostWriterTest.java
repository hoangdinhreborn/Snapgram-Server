package com.example.recommender.batch;

import com.example.recommender.batch.job.trending.TrendingPostWriter;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendingPostWriterTest {

    @Mock
    private RecommenderTrendingPostRepository trendingPostRepository;

    @InjectMocks
    private TrendingPostWriter writer;

    @Test
    @DisplayName("Write chunk: lưu toàn bộ bài viết trong chunk vào repository")
    void testWriteChunk_ShouldSaveAll() throws Exception {
        RecommenderTrendingPost p1 = RecommenderTrendingPost.builder()
                .id(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .score(0.95)
                .calculatedAt(Instant.now())
                .build();

        Chunk<RecommenderTrendingPost> chunk = new Chunk<>(List.of(p1));
        writer.write(chunk);

        verify(trendingPostRepository, times(1)).saveAll(chunk.getItems());
    }
}
