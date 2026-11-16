package com.example.recommender.batch;

import com.example.recommender.controller.RecommendationController;
import com.example.recommender.dto.recommendation.TrendingPostDto;
import com.example.recommender.dto.recommendation.UserRecommendationDto;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.repository.RecommenderTrendingPostRepository;
import com.example.recommender.repository.RecommenderUserRecommendationRepository;
import com.example.recommender.util.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    @Mock
    private RecommenderTrendingPostRepository trendingPostRepository;

    @Mock
    private RecommenderUserRecommendationRepository recommendationRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RecommendationController controller;

    private MockedStatic<UserContext> mockedUserContext;

    @BeforeEach
    void setUp() {
        mockedUserContext = mockStatic(UserContext.class);
    }

    @AfterEach
    void tearDown() {
        mockedUserContext.close();
    }

    @Test
    @DisplayName("Trending Posts - Cache Hit: đọc trực tiếp từ Redis")
    void testGetTrendingPosts_CacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("trending:posts")).thenReturn("[{\"postId\":\"11111111-1111-1111-1111-111111111111\"}]");

        TrendingPostDto dto = TrendingPostDto.builder()
                .postId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .score(0.99)
                .build();
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(List.of(dto));

        ResponseEntity<List<TrendingPostDto>> response = controller.getTrendingPosts(10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(dto.getPostId(), response.getBody().get(0).getPostId());
        verify(trendingPostRepository, never()).findLatestTrending(any());
    }

    @Test
    @DisplayName("Trending Posts - Cache Miss: fallback truy vấn Database")
    void testGetTrendingPosts_CacheMiss_FallbackDb() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("trending:posts")).thenReturn(null);

        RecommenderTrendingPost post = RecommenderTrendingPost.builder()
                .id(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .contentType("IMAGE")
                .score(0.88)
                .calculatedAt(Instant.now())
                .build();

        when(trendingPostRepository.findTopTrending(any(Pageable.class))).thenReturn(List.of(post));

        ResponseEntity<List<TrendingPostDto>> response = controller.getTrendingPosts(10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(post.getPostId(), response.getBody().get(0).getPostId());
    }

    @Test
    @DisplayName("User Recommendations - Cache Miss: fallback truy vấn Database với UserContext")
    void testGetUserRecommendations_CacheMiss_FallbackDb() {
        UUID currentUserId = UUID.randomUUID();
        mockedUserContext.when(UserContext::getCurrentUserId).thenReturn(currentUserId);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:user:" + currentUserId)).thenReturn(null);

        RecommenderUserRecommendation rec = RecommenderUserRecommendation.builder()
                .id(UUID.randomUUID())
                .userId(currentUserId)
                .recommendedUserId(UUID.randomUUID())
                .score(0.75)
                .mutualFriendsCount(2)
                .reason("Có 2 bạn chung")
                .calculatedAt(Instant.now())
                .build();

        when(recommendationRepository.findTopRecommendationsForUser(eq(currentUserId), any(Pageable.class)))
                .thenReturn(List.of(rec));

        ResponseEntity<List<UserRecommendationDto>> response = controller.getUserRecommendations(10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(rec.getRecommendedUserId(), response.getBody().get(0).getRecommendedUserId());
    }
}
