package com.example.recommender.batch.job.trending;

import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@StepScope
@Slf4j
@RequiredArgsConstructor
public class TrendingPostWriter implements ItemWriter<RecommenderTrendingPost> {

    private final RecommenderTrendingPostRepository trendingPostRepository;

    @Override
    public void write(Chunk<? extends RecommenderTrendingPost> chunk) throws Exception {
        List<? extends RecommenderTrendingPost> items = chunk.getItems();
        if (items.isEmpty()) return;

        trendingPostRepository.saveAll(items);
        log.debug("Đã ghi thành công chunk {} bài viết trending vào DB", items.size());
    }
}
