package com.example.recommender.batch.job.trending;

import com.example.recommender.batch.config.DemoFailureSwitch;
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
import java.util.concurrent.atomic.AtomicInteger;

@Component
@StepScope
@Slf4j
@RequiredArgsConstructor
public class TrendingPostProcessor implements ItemProcessor<RecommenderPostView, RecommenderTrendingPost> {

    private final RecommenderInteractionViewRepository interactionViewRepository;
    private final RecommendationScoringEngine scoringEngine;
    private final DemoFailureSwitch demoFailureSwitch;

    private final AtomicInteger processedCounter = new AtomicInteger(0);

    @Setter
    @Value("#{jobParameters['failAtItem'] ?: 0}")
    private int failAtItem;

    @Setter
    @Value("#{jobParameters['timestamp'] ?: T(java.lang.System).currentTimeMillis()}")
    private long runTimestamp;

    @Override
    public RecommenderTrendingPost process(RecommenderPostView post) throws Exception {
        int currentCount = processedCounter.incrementAndGet();

        // 1. Giả lập lỗi để minh họa tính năng RESTART của Spring Batch
        // Sử dụng DemoFailureSwitch để đảm bảo chỉ ném lỗi đúng 1 LẦN khi runJob, không fail lặp lại khi restart!
        if (failAtItem > 0 && currentCount == failAtItem && demoFailureSwitch.tryTrigger()) {
            log.error("💥 [DEMO RESTART] Cố tình ném lỗi gián đoạn tại item #{} theo cấu hình failAtItem={}",
                    currentCount, failAtItem);
            throw new IllegalStateException("Cố tình mô phỏng sự cố hệ thống tại item #" + currentCount
                    + " để demo khả năng RESTART của Spring Batch!");
        }

        // 2. Phát hiện dữ liệu bẩn để minh họa tính năng SKIP (Fault Tolerance)
        if (post == null || post.getPostId() == null) {
            throw new MalformedPostException("Bản ghi bài viết không hợp lệ: postId bị null");
        }
        if (post.getCaption() != null && post.getCaption().contains("DEMO_MALFORMED")) {
            throw new MalformedPostException("Bản ghi bài viết chứa nội dung bẩn cố ý 'DEMO_MALFORMED': "
                    + post.getPostId());
        }

        // 3. Tính toán số liệu tương tác (Null-safe)
        long likes = interactionViewRepository.countLikesByPostId(post.getPostId());
        Double avgWatchTime = interactionViewRepository.findAvgWatchTimeRatioByPostId(post.getPostId());

        // 4. Mốc thời gian chung thống nhất cho cả JobExecution (lấy từ jobParameters['timestamp'])
        // Giúp tính điểm time-decay và calculatedAt đồng nhất tuyệt đối giữa các chunk và khi restart
        Instant executionTime = Instant.ofEpochMilli(runTimestamp);
        double score = scoringEngine.calculateExploreScore(post, executionTime, likes, avgWatchTime);
        double roundedScore = Math.round(score * 1000.0) / 1000.0;

        // 5. Gán ID tất định id = post.getPostId() để đảm bảo tính Idempotency (không bị nhân đôi bản ghi khi chạy lại)
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
