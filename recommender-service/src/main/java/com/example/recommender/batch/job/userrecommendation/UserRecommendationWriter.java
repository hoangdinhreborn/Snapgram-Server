package com.example.recommender.batch.job.userrecommendation;

import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.repository.RecommenderUserRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@StepScope
@Slf4j
@RequiredArgsConstructor
public class UserRecommendationWriter implements ItemWriter<List<RecommenderUserRecommendation>> {

    private final RecommenderUserRecommendationRepository recommendationRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_PREFIX = "recommend:user:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @Override
    public void write(Chunk<? extends List<RecommenderUserRecommendation>> chunk) throws Exception {
        List<RecommenderUserRecommendation> toSave = new ArrayList<>();

        for (List<RecommenderUserRecommendation> list : chunk.getItems()) {
            if (list != null && !list.isEmpty()) {
                toSave.addAll(list);

                // Cache Redis cho từng user
                UUID userId = list.get(0).getUserId();
                try {
                    String key = REDIS_PREFIX + userId;
                    String json = objectMapper.writeValueAsString(list);
                    redisTemplate.opsForValue().set(key, json, CACHE_TTL);
                } catch (Exception e) {
                    log.warn("redis_cache_user_rec_failed userId={} reason={}", userId, e.getMessage());
                }
            }
        }

        if (!toSave.isEmpty()) {
            recommendationRepository.saveAll(toSave);
            log.debug("user_recommendations_saved count={}", toSave.size());
        }
    }
}
