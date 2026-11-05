package com.example.recommender.batch.job.trending;

import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrendingPostRedisUpdateListener implements StepExecutionListener {

    private final RecommenderTrendingPostRepository trendingPostRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public static final String REDIS_TRENDING_KEY = "trending:posts";
    public static final Duration CACHE_TTL = Duration.ofHours(6);

    @Override
    @Transactional
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus().isUnsuccessful()) {
            return stepExecution.getExitStatus();
        }

        try {
            // Đọc Top 50 bài viết có score cao nhất từ DB (sắp xếp theo score DESC, postId ASC)
            List<RecommenderTrendingPost> topPosts = trendingPostRepository.findTopTrending(PageRequest.of(0, 50));
            if (!topPosts.isEmpty()) {
                // Gán rank 1..N và cập nhật lại DB
                for (int i = 0; i < topPosts.size(); i++) {
                    topPosts.get(i).setRank(i + 1);
                }
                trendingPostRepository.saveAll(topPosts);

                // Ghi Top 50 vào Redis cache
                String json = objectMapper.writeValueAsString(topPosts);
                redisTemplate.opsForValue().set(REDIS_TRENDING_KEY, json, CACHE_TTL);
                log.info("🔥 [REDIS CACHE UPDATED] Đã lưu Top {} bài viết trending (rank 1-{}) vào key '{}' (TTL: {})",
                        topPosts.size(), topPosts.size(), REDIS_TRENDING_KEY, CACHE_TTL);
            }
        } catch (Exception e) {
            log.warn("Không thể cập nhật Redis cache cho trending posts: {}", e.getMessage());
        }

        return stepExecution.getExitStatus();
    }
}
