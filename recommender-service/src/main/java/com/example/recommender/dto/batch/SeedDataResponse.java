package com.example.recommender.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedDataResponse {
    private int postsCreated;
    private int interactionsCreated;
    private int followsCreated;
    private int malformedPostsCreated;
    private String message;
}
