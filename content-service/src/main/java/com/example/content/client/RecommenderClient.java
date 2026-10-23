package com.example.content.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
@Slf4j
public class RecommenderClient {

    private final RestClient restClient;

    public RecommenderClient(@Value("${app.recommender.url:http://recommender-service:8090}") String recommenderUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1200);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(recommenderUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public List<RecommendedItem> getPersonalFeedRecommendations(UUID userId, int page, int size) {
        try {
            FeedResponseDto res = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/feed")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .header("X-User-Id", userId.toString())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(FeedResponseDto.class);

            return res != null && res.getItems() != null ? res.getItems() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Recommender personal feed call failed, falling back to db order: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<RecommendedItem> getExploreFeedRecommendations(UUID userId, int page, int size) {
        try {
            FeedResponseDto res = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/feed/explore")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .headers(headers -> {
                        if (userId != null) {
                            headers.set("X-User-Id", userId.toString());
                        }
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(FeedResponseDto.class);

            return res != null && res.getItems() != null ? res.getItems() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Recommender explore feed call failed, falling back to db order: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedResponseDto {
        private List<RecommendedItem> items;
        private int page;
        private int size;
        private long totalElements;
        private boolean hasNext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedItem {
        private UUID postId;
        private UUID authorId;
        private String contentType;
        private String caption;
        private String tags;
        private String visibility;
        private double score;
        private String recommendationReason;
    }
}
