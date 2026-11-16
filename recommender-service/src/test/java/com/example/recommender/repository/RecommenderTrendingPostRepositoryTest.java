package com.example.recommender.repository;

import com.example.recommender.entity.RecommenderTrendingPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecommenderTrendingPostRepositoryTest {

    @Autowired
    private RecommenderTrendingPostRepository repository;

    @Test
    @DisplayName("findTopTrending: sắp xếp chính xác theo score DESC, postId ASC (tie-breaker duy nhất)")
    void testFindTopTrending_SortingOrder() {
        UUID idA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID idC = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // Post 1: score 0.5, id B
        RecommenderTrendingPost p1 = RecommenderTrendingPost.builder()
                .id(idB).postId(idB).authorId(UUID.randomUUID()).contentType("POST")
                .score(0.5).calculatedAt(Instant.now()).rank(0).build();

        // Post 2: score 1.0, id C
        RecommenderTrendingPost p2 = RecommenderTrendingPost.builder()
                .id(idC).postId(idC).authorId(UUID.randomUUID()).contentType("POST")
                .score(1.0).calculatedAt(Instant.now()).rank(0).build();

        // Post 3: score 1.0, id A (cùng score 1.0 với C, nhưng id A < id C)
        RecommenderTrendingPost p3 = RecommenderTrendingPost.builder()
                .id(idA).postId(idA).authorId(UUID.randomUUID()).contentType("POST")
                .score(1.0).calculatedAt(Instant.now()).rank(0).build();

        repository.saveAll(List.of(p1, p2, p3));

        List<RecommenderTrendingPost> results = repository.findTopTrending(PageRequest.of(0, 10));

        assertEquals(3, results.size());
        assertEquals(idA, results.get(0).getPostId());
        assertEquals(idC, results.get(1).getPostId());
        assertEquals(idB, results.get(2).getPostId());
    }
}
