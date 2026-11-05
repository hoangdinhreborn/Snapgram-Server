package com.example.recommender.controller;

import com.example.recommender.dto.recommendation.TrendingPostDto;
import com.example.recommender.dto.recommendation.UserRecommendationDto;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import com.example.recommender.repository.RecommenderUserRecommendationRepository;
import com.example.recommender.util.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recommendation Results API", description = "Các API đọc kết quả recommendation và trending đã được Spring Batch tính toán")
public class RecommendationController {

    private final RecommenderTrendingPostRepository trendingPostRepository;
    private final RecommenderUserRecommendationRepository recommendationRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_TRENDING_KEY = "trending:posts";
    private static final String REDIS_USER_REC_PREFIX = "recommend:user:";

    @Operation(summary = "Lấy danh sách Top bài viết thịnh hành (được tính toán bởi Batch Job, ưu tiên đọc Redis)")
    @GetMapping("/trending")
    public ResponseEntity<List<TrendingPostDto>> getTrendingPosts(
            @RequestParam(defaultValue = "20") int limit) {
        limit = Math.min(Math.max(1, limit), 50);

        // 1. Thử đọc từ Redis Cache
        try {
            String cachedJson = redisTemplate.opsForValue().get(REDIS_TRENDING_KEY);
            if (cachedJson != null && !cachedJson.isBlank()) {
                List<TrendingPostDto> cached = objectMapper.readValue(cachedJson, new TypeReference<List<TrendingPostDto>>() {});
                if (!cached.isEmpty()) {
                    return ResponseEntity.ok(cached.stream().limit(limit).toList());
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi đọc Redis cache '{}': {}", REDIS_TRENDING_KEY, e.getMessage());
        }

        // 2. Cache Miss: Đọc từ Database bảng kết quả batch (sắp xếp theo score DESC, postId ASC)
        List<RecommenderTrendingPost> dbList = trendingPostRepository.findTopTrending(PageRequest.of(0, limit));
        List<TrendingPostDto> result = dbList.stream()
                .map(p -> TrendingPostDto.builder()
                        .postId(p.getPostId())
                        .authorId(p.getAuthorId())
                        .contentType(p.getContentType())
                        .caption(p.getCaption())
                        .tags(p.getTags())
                        .score(p.getScore())
                        .likesCount(p.getLikesCount())
                        .rank(p.getRank())
                        .calculatedAt(p.getCalculatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Lấy danh sách gợi ý người dùng nên theo dõi cho user hiện tại (ưu tiên đọc Redis)")
    @GetMapping("/users")
    public ResponseEntity<List<UserRecommendationDto>> getUserRecommendations(
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = UserContext.getCurrentUserId();
        limit = Math.min(Math.max(1, limit), 30);

        // 1. Thử đọc từ Redis Cache
        String redisKey = REDIS_USER_REC_PREFIX + userId;
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                List<UserRecommendationDto> cached = objectMapper.readValue(cachedJson, new TypeReference<List<UserRecommendationDto>>() {});
                if (!cached.isEmpty()) {
                    return ResponseEntity.ok(cached.stream().limit(limit).toList());
                }
            }
        } catch (Exception e) {
            log.warn("Lỗi đọc Redis cache '{}': {}", redisKey, e.getMessage());
        }

        // 2. Cache Miss: Đọc từ Database bảng kết quả batch
        List<RecommenderUserRecommendation> dbList = recommendationRepository.findTopRecommendationsForUser(userId, PageRequest.of(0, limit));
        List<UserRecommendationDto> result = dbList.stream()
                .map(r -> UserRecommendationDto.builder()
                        .userId(r.getUserId())
                        .recommendedUserId(r.getRecommendedUserId())
                        .score(r.getScore())
                        .mutualFriendsCount(r.getMutualFriendsCount())
                        .reason(r.getReason())
                        .calculatedAt(r.getCalculatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(result);
    }
}
