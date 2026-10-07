package com.example.recommender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedResponse {
    private List<RecommendedPostDto> items;
    private int page;
    private int size;
    private long totalElements;
    private boolean hasNext;
}
