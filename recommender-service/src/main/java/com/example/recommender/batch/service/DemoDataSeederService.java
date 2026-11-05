package com.example.recommender.batch.service;

import com.example.recommender.dto.batch.SeedDataResponse;
import com.example.recommender.entity.RecommenderFollowView;
import com.example.recommender.entity.RecommenderInteractionView;
import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DemoDataSeederService {

    private final RecommenderPostViewRepository postViewRepository;
    private final RecommenderInteractionViewRepository interactionViewRepository;
    private final RecommenderFollowViewRepository followViewRepository;
    private final RecommenderTrendingPostRepository trendingPostRepository;
    private final RecommenderUserRecommendationRepository userRecommendationRepository;

    @Value("${recommender.batch.demo.enabled:true}")
    private boolean demoEnabled;

    private static final String[] SAMPLE_TAGS = {
            "travel,vietnam,wanderlust",
            "foodie,coffee,saigon",
            "coding,java,spring",
            "fitness,gym,lifestyle",
            "photography,sunset,nature",
            "tech,ai,developer"
    };

    @Transactional
    public SeedDataResponse seedDemoData(int count) {
        if (!demoEnabled) {
            throw new IllegalStateException("Endpoint seed demo data bị tắt trên môi trường này (recommender.batch.demo.enabled=false).");
        }

        count = Math.min(Math.max(50, count), 20000);
        log.info("Bắt đầu dọn dẹp dữ liệu cũ và seed {} bản ghi demo cho Spring Batch...", count);

        // 1. Dọn dẹp dữ liệu cũ để đảm bảo tính Idempotent cho môi trường demo
        interactionViewRepository.deleteAllInBatch();
        postViewRepository.deleteAllInBatch();
        followViewRepository.deleteAllInBatch();
        trendingPostRepository.deleteAllInBatch();
        userRecommendationRepository.deleteAllInBatch();

        // Lưu ý: Random(42) chỉ cố định chuỗi phân nhánh lựa chọn pseudo-random;
        // các giá trị UUID và Instant.now() vẫn sẽ được sinh mới ở mỗi lần seed.
        Random random = new Random(42);
        Instant now = Instant.now();

        // 2. Tạo tập 50 user nòng cốt
        List<UUID> users = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            users.add(UUID.randomUUID());
        }

        // 3. Tạo posts
        List<RecommenderPostView> posts = new ArrayList<>();
        int malformedCount = 3;

        // 3.1 Tạo bài viết hợp lệ (rải theo phút trong 30 ngày gần nhất)
        for (int i = 0; i < count - malformedCount; i++) {
            UUID author = users.get(random.nextInt(users.size()));
            Instant createdAt = now.minus(random.nextInt(30 * 24 * 60), ChronoUnit.MINUTES);
            String caption = "Khám phá Snapgram bài viết số #" + (i + 1) + " từ tác giả tuyệt vời!";
            String tags = SAMPLE_TAGS[random.nextInt(SAMPLE_TAGS.length)];

            posts.add(RecommenderPostView.builder()
                    .postId(UUID.randomUUID())
                    .authorId(author)
                    .contentType(random.nextBoolean() ? "IMAGE" : "VIDEO")
                    .caption(caption)
                    .tags(tags)
                    .visibility("PUBLIC")
                    .authorIsPrivate(false)
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .build());
        }

        // 3.2 Tạo 3 bài viết cố tình chứa cờ lỗi DEMO_MALFORMED
        // Gán createdAt = now - 31 ngày để khi sort theo createdAt DESC, 3 bài này luôn nằm ở CHUNK CUỐI CÙNG
        // Giúp kịch bản mô phỏng restart ở item 45 không bị ảnh hưởng bởi lỗi skip!
        for (int i = 0; i < malformedCount; i++) {
            UUID author = users.get(random.nextInt(users.size()));
            Instant createdAt = now.minus(31, ChronoUnit.DAYS).plusSeconds(i);
            String caption = "DEMO_MALFORMED - Post #" + (i + 1) + " intended to trigger Spring Batch SkipListener";
            String tags = SAMPLE_TAGS[random.nextInt(SAMPLE_TAGS.length)];

            posts.add(RecommenderPostView.builder()
                    .postId(UUID.randomUUID())
                    .authorId(author)
                    .contentType("IMAGE")
                    .caption(caption)
                    .tags(tags)
                    .visibility("PUBLIC")
                    .authorIsPrivate(false)
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .build());
        }

        postViewRepository.saveAll(posts);

        // 4. Tạo interactions (likes, views)
        List<RecommenderInteractionView> interactions = new ArrayList<>();
        int interactionCount = Math.min(count * 2, 30000);

        for (int i = 0; i < interactionCount; i++) {
            RecommenderPostView targetPost = posts.get(random.nextInt(posts.size()));
            UUID interactingUser = users.get(random.nextInt(users.size()));

            boolean isLike = random.nextBoolean();
            interactions.add(RecommenderInteractionView.builder()
                    .id(UUID.randomUUID())
                    .userId(interactingUser)
                    .postId(targetPost.getPostId())
                    .type(isLike ? "LIKE" : "VIEW")
                    .watchTimeRatio(BigDecimal.valueOf(0.2 + (0.8 * random.nextDouble())))
                    .explicitRating(isLike ? BigDecimal.valueOf(5.0) : null)
                    .createdAt(targetPost.getCreatedAt().plus(random.nextInt(60), ChronoUnit.MINUTES))
                    .build());
        }
        interactionViewRepository.saveAll(interactions);

        // 5. Tạo quan hệ follow để phục vụ User Recommendation (Friend-of-Friends)
        List<RecommenderFollowView> follows = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            UUID u1 = users.get(i);
            int numFollows = 5 + random.nextInt(10);
            for (int j = 0; j < numFollows; j++) {
                UUID u2 = users.get(random.nextInt(users.size()));
                if (!u1.equals(u2)) {
                    follows.add(RecommenderFollowView.builder()
                            .id(new RecommenderFollowView.RecommenderFollowId(u1, u2))
                            .status("ACCEPTED")
                            .createdAt(now.minus(random.nextInt(60), ChronoUnit.DAYS))
                            .build());
                }
            }
        }

        // Thêm 1 user dị thường có ID bắt đầu bằng 00000000 để demo skip trong User Recommendation Job
        UUID malformedUser = UUID.fromString("00000000-0000-0000-0000-000000000001");
        follows.add(RecommenderFollowView.builder()
                .id(new RecommenderFollowView.RecommenderFollowId(malformedUser, users.get(0)))
                .status("ACCEPTED")
                .createdAt(now)
                .build());

        followViewRepository.saveAll(follows);

        log.info("Hoàn tất seed dữ liệu: {} posts ({} malformed), {} interactions, {} follows",
                posts.size(), malformedCount, interactions.size(), follows.size());

        return SeedDataResponse.builder()
                .postsCreated(posts.size())
                .interactionsCreated(interactions.size())
                .followsCreated(follows.size())
                .malformedPostsCreated(malformedCount)
                .message("Đã nạp thành công dữ liệu mẫu cho Spring Batch Demo!")
                .build();
    }
}
