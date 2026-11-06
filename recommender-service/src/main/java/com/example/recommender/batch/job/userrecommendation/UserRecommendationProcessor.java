package com.example.recommender.batch.job.userrecommendation;

import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderBlockViewRepository;
import com.example.recommender.repository.RecommenderFollowViewRepository;
import com.example.recommender.repository.RecommenderMuteViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
@StepScope
@Slf4j
@RequiredArgsConstructor
public class UserRecommendationProcessor implements ItemProcessor<UUID, List<RecommenderUserRecommendation>> {

    private final RecommenderFollowViewRepository followViewRepository;
    private final RecommenderBlockViewRepository blockViewRepository;
    private final RecommenderMuteViewRepository muteViewRepository;

    private static final int MAX_RECOMMENDATIONS_PER_USER = 10;

    @Setter
    @Value("#{jobParameters['timestamp'] ?: T(java.lang.System).currentTimeMillis()}")
    private long runTimestamp;

    @Override
    public List<RecommenderUserRecommendation> process(UUID userId) throws Exception {
        if (userId == null) {
            throw new MalformedPostException("User ID không được để null");
        }

        // Tập hợp danh sách cần loại trừ (chính mình, người đã follow, người chặn/mute)
        Set<UUID> excluded = new HashSet<>();
        excluded.add(userId);
        excluded.addAll(followViewRepository.findFollowingIds(userId));
        excluded.addAll(blockViewRepository.findAllBlockedAndBlockerUserIds(userId));
        excluded.addAll(muteViewRepository.findMutedUserIds(userId));

        List<RecommenderUserRecommendation> result = new ArrayList<>();
        Instant executionTime = Instant.ofEpochMilli(runTimestamp);

        // Gợi ý theo Friend-of-Friends (bạn chung)
        List<Object[]> fofList = followViewRepository.findFriendOfFriendsWithMutualCount(userId);
        for (Object[] row : fofList) {
            UUID candidateId = (UUID) row[0];
            long mutualCount = ((Number) row[1]).longValue();

            if (!excluded.contains(candidateId)) {
                double score = Math.min(1.0, 0.5 + (0.1 * mutualCount));
                // ID tất định dựa trên (userId + ":" + candidateId) đảm bảo Idempotent khi chạy lại job
                UUID deterministicId = UUID.nameUUIDFromBytes((userId.toString() + ":" + candidateId.toString()).getBytes(StandardCharsets.UTF_8));

                result.add(RecommenderUserRecommendation.builder()
                        .id(deterministicId)
                        .userId(userId)
                        .recommendedUserId(candidateId)
                        .score(Math.round(score * 1000.0) / 1000.0)
                        .mutualFriendsCount(mutualCount)
                        .reason("Có " + mutualCount + " người bạn chung theo dõi")
                        .calculatedAt(executionTime)
                        .build());

                if (result.size() >= MAX_RECOMMENDATIONS_PER_USER) {
                    break;
                }
            }
        }

        return result;
    }
}
