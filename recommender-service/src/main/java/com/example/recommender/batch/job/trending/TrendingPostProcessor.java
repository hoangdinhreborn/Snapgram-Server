package com.example.recommender.batch.job.trending;

import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderInteractionViewRepository;
import com.example.recommender.service.RecommendationScoringEngine;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * ItemProcessor chấm điểm xu hướng cho từng bài viết công khai.
 * Thiết kế Stateless (không giữ trạng thái giữa các item).
 * Tiêu chí bản ghi hỏng (ném MalformedPostException để Fault-Tolerant Skip):
 * 1. postId null
 * 2. authorId null
 * 3. createdAt null
 * 4. contentType null, rỗng hoặc không thuộc danh mục hợp lệ
 */
@Component
@StepScope
@Slf4j
@RequiredArgsConstructor
public class TrendingPostProcessor implements ItemProcessor<RecommenderPostView, RecommenderTrendingPost> {

    private static final Set<String> VALID_CONTENT_TYPES = Set.of(
            "POST", "IMAGE", "VIDEO", "CAROUSEL", "REEL", "TEXT", "STORY"
    );

    private final RecommenderInteractionViewRepository interactionViewRepository;
    private final RecommendationScoringEngine scoringEngine;

    @Setter
    @Value("#{jobParameters['timestamp'] ?: T(java.lang.System).currentTimeMillis()}")
    private long runTimestamp;

    @Override
    public RecommenderTrendingPost process(RecommenderPostView post) throws Exception {
        // 1. Kiểm tra tính toàn vẹn dữ liệu (Malformed Record Validation)
        if (post == null || post.getPostId() == null) {
            throw new MalformedPostException("Bản ghi bài viết không hợp lệ: postId bị null");
        }
        if (post.getAuthorId() == null) {
            throw new MalformedPostException("Bản ghi bài viết không hợp lệ: authorId bị null (postId=" + post.getPostId() + ")");
        }
        if (post.getCreatedAt() == null) {
            throw new MalformedPostException("Bản ghi bài viết không hợp lệ: createdAt bị null (postId=" + post.getPostId() + ")");
        }
        if (post.getContentType() == null || post.getContentType().isBlank()
                || !VALID_CONTENT_TYPES.contains(post.getContentType().toUpperCase())) {
            throw new MalformedPostException(String.format(
                    "Bản ghi bài viết không hợp lệ: contentType '%s' không được hỗ trợ (postId=%s)",
                    post.getContentType(), post.getPostId()));
        }

        // 2. Tính toán số liệu tương tác (Null-safe)
        long likes = interactionViewRepository.countLikesByPostId(post.getPostId());
        Double avgWatchTime = interactionViewRepository.findAvgWatchTimeRatioByPostId(post.getPostId());

        // 3. Mốc thời gian chung thống nhất cho cả JobExecution (lấy từ jobParameters['timestamp'])
        Instant executionTime = Instant.ofEpochMilli(runTimestamp);
        double score = scoringEngine.calculateExploreScore(post, executionTime, likes, avgWatchTime);
        double roundedScore = Math.round(score * 1000.0) / 1000.0;

        // 4. Gán ID tất định id = post.getPostId() để đảm bảo tính Idempotency khi ghi DB/Redis
        return RecommenderTrendingPost.builder()
                .id(post.getPostId())
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .contentType(post.getContentType())
                .caption(post.getCaption())
                .tags(post.getTags())
                .likesCount(likes)
                .score(roundedScore)
                .rank(0) // Sẽ được cập nhật thứ hạng theo Top N
                .calculatedAt(executionTime)
                .build();
    }
}
