package com.example.recommender.service;

import com.example.recommender.dto.FeedResponse;
import com.example.recommender.dto.RecommendedPostDto;
import com.example.recommender.dto.SuggestedUserDto;
import com.example.recommender.dto.TrendingTagDto;
import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedService {

    private final RecommenderPostViewRepository postViewRepository;
    private final RecommenderInteractionViewRepository interactionViewRepository;
    private final RecommenderFollowViewRepository followViewRepository;
    private final RecommenderBlockViewRepository blockViewRepository;
    private final RecommenderMuteViewRepository muteViewRepository;
    private final RecommendationScoringEngine scoringEngine;

    @Transactional(readOnly = true)
    public FeedResponse getPersonalFeed(UUID userId, int page, int size) {
        // 1. Tập hợp following + self
        List<UUID> followingIds = followViewRepository.findFollowingIds(userId);
        Set<UUID> authorIds = new HashSet<>(followingIds);
        authorIds.add(userId);

        // 2. Tập hợp user bị chặn và bị mute để loại trừ triệt để
        Set<UUID> excludedAuthors = getExcludedAuthorIds(userId);

        // 3. Lấy ứng viên bài đăng
        List<RecommenderPostView> candidates = postViewRepository.findFeedCandidates(
                authorIds,
                excludedAuthors.isEmpty() ? null : excludedAuthors,
                PageRequest.of(0, 150));

        // 4. Lấy thông tin tương tác quá khứ của user để tính độ thân thiết (Affinity)
        Map<UUID, Long> affinityMap = new HashMap<>();
        for (Object[] row : interactionViewRepository.findAffinityCountsByUserId(userId)) {
            UUID author = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            affinityMap.put(author, count);
        }

        // 5. Tính điểm xếp hạng từng bài viết
        Instant now = Instant.now();
        List<RecommendedPostDto> scoredPosts = candidates.stream()
                .map(post -> {
                    long likes = interactionViewRepository.countLikesByPostId(post.getPostId());
                    Double avgWatchTime = interactionViewRepository.findAvgWatchTimeRatioByPostId(post.getPostId());
                    double score = scoringEngine.calculatePersonalScore(post, now, affinityMap, likes, avgWatchTime);

                    String reason = post.getAuthorId().equals(userId)
                            ? "Bài viết của bạn"
                            : affinityMap.containsKey(post.getAuthorId())
                            ? "Từ người bạn thường xuyên tương tác"
                            : "Từ người bạn đang theo dõi";

                    return RecommendedPostDto.builder()
                            .postId(post.getPostId())
                            .authorId(post.getAuthorId())
                            .contentType(post.getContentType())
                            .caption(post.getCaption())
                            .tags(post.getTags())
                            .visibility(post.getVisibility())
                            .createdAt(post.getCreatedAt())
                            .score(Math.round(score * 1000.0) / 1000.0)
                            .recommendationReason(reason)
                            .build();
                })
                .sorted(Comparator.comparingDouble(RecommendedPostDto::getScore).reversed())
                .toList();

        // 6. Phân trang
        return paginate(scoredPosts, page, size);
    }

    @Transactional(readOnly = true)
    public FeedResponse getExploreFeed(UUID userId, int page, int size) {
        Instant now = Instant.now();
        Instant since = now.minus(14, ChronoUnit.DAYS); // Ưu tiên bài 14 ngày gần nhất

        Set<UUID> excludedAuthors = userId != null ? getExcludedAuthorIds(userId) : Collections.emptySet();
        Set<UUID> followingAndSelf = new HashSet<>();
        if (userId != null) {
            followingAndSelf.addAll(followViewRepository.findFollowingIds(userId));
            followingAndSelf.add(userId);
        }

        List<RecommenderPostView> candidates;
        if (!followingAndSelf.isEmpty()) {
            candidates = postViewRepository.findExploreCandidates(
                    followingAndSelf,
                    excludedAuthors.isEmpty() ? null : excludedAuthors,
                    since,
                    PageRequest.of(0, 150));
        } else {
            candidates = postViewRepository.findLatestPublicPosts(
                    excludedAuthors.isEmpty() ? null : excludedAuthors,
                    PageRequest.of(0, 150));
        }

        // Nếu ít ứng viên, nạp thêm bài public mới nhất
        if (candidates.size() < size) {
            candidates = postViewRepository.findLatestPublicPosts(
                    excludedAuthors.isEmpty() ? null : excludedAuthors,
                    PageRequest.of(0, 150));
        }

        List<RecommendedPostDto> scoredPosts = candidates.stream()
                .map(post -> {
                    long likes = interactionViewRepository.countLikesByPostId(post.getPostId());
                    Double avgWatchTime = interactionViewRepository.findAvgWatchTimeRatioByPostId(post.getPostId());
                    double score = scoringEngine.calculateExploreScore(post, now, likes, avgWatchTime);

                    return RecommendedPostDto.builder()
                            .postId(post.getPostId())
                            .authorId(post.getAuthorId())
                            .contentType(post.getContentType())
                            .caption(post.getCaption())
                            .tags(post.getTags())
                            .visibility(post.getVisibility())
                            .createdAt(post.getCreatedAt())
                            .score(Math.round(score * 1000.0) / 1000.0)
                            .recommendationReason("Thịnh hành trên Snapgram")
                            .build();
                })
                .sorted(Comparator.comparingDouble(RecommendedPostDto::getScore).reversed())
                .toList();

        return paginate(scoredPosts, page, size);
    }

    @Transactional(readOnly = true)
    public List<SuggestedUserDto> getSuggestedUsers(UUID userId, int limit) {
        Set<UUID> excluded = getExcludedAuthorIds(userId);
        excluded.add(userId);
        excluded.addAll(followViewRepository.findFollowingIds(userId));

        List<SuggestedUserDto> suggestions = new ArrayList<>();

        // 1. Gợi ý theo bạn chung (Friend of friends)
        List<Object[]> fofList = followViewRepository.findFriendOfFriendsWithMutualCount(userId);
        for (Object[] row : fofList) {
            UUID candidateId = (UUID) row[0];
            long mutualCount = ((Number) row[1]).longValue();
            if (!excluded.contains(candidateId)) {
                suggestions.add(SuggestedUserDto.builder()
                        .userId(candidateId)
                        .mutualFriendsCount(mutualCount)
                        .reason("Có " + mutualCount + " người bạn chung theo dõi")
                        .build());
                if (suggestions.size() >= limit) break;
            }
        }

        return suggestions;
    }

    @Cacheable(value = "trending_tags", key = "'all'")
    @Transactional(readOnly = true)
    public List<TrendingTagDto> getTrendingTags(int limit) {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<String> rawTagList = postViewRepository.findRecentTags(since);

        Map<String, Long> tagCounts = new HashMap<>();
        for (String raw : rawTagList) {
            if (raw == null || raw.isBlank()) continue;
            String[] tags = raw.split("[,\\s]+");
            for (String t : tags) {
                String clean = t.trim().toLowerCase().replaceAll("[^a-z0-9_]", "");
                if (!clean.isBlank()) {
                    tagCounts.put(clean, tagCounts.getOrDefault(clean, 0L) + 1);
                }
            }
        }

        return tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> TrendingTagDto.builder()
                        .tag(e.getKey())
                        .postCount(e.getValue())
                        .build())
                .toList();
    }

    private Set<UUID> getExcludedAuthorIds(UUID userId) {
        Set<UUID> excluded = new HashSet<>();
        excluded.addAll(blockViewRepository.findAllBlockedAndBlockerUserIds(userId));
        excluded.addAll(muteViewRepository.findMutedUserIds(userId));
        return excluded;
    }

    private FeedResponse paginate(List<RecommendedPostDto> list, int page, int size) {
        int total = list.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<RecommendedPostDto> items = (fromIndex < toIndex) ? list.subList(fromIndex, toIndex) : Collections.emptyList();

        return FeedResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(total)
                .hasNext(toIndex < total)
                .build();
    }
}
