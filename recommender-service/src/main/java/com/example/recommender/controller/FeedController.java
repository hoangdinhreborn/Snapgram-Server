package com.example.recommender.controller;

import com.example.recommender.dto.FeedResponse;
import com.example.recommender.dto.SuggestedUserDto;
import com.example.recommender.dto.TrendingTagDto;
import com.example.recommender.service.FeedService;
import com.example.recommender.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@Tag(name = "Feed & Recommendation API", description = "Các API cung cấp newsfeed cá nhân hoá, khám phá và gợi ý")
public class FeedController {

    private final FeedService feedService;

    @Operation(summary = "Lấy feed trang chủ cá nhân hoá")
    @GetMapping
    public ResponseEntity<FeedResponse> getPersonalFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(Math.max(1, size), 50);
        return ResponseEntity.ok(feedService.getPersonalFeed(userId, page, size));
    }

    @Operation(summary = "Lấy feed khám phá (Explore)")
    @GetMapping("/explore")
    public ResponseEntity<FeedResponse> getExploreFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserIdOrNull();
        size = Math.min(Math.max(1, size), 50);
        return ResponseEntity.ok(feedService.getExploreFeed(userId, page, size));
    }

    @Operation(summary = "Gợi ý người dùng nên theo dõi (Suggested Users)")
    @GetMapping("/suggested-users")
    public ResponseEntity<List<SuggestedUserDto>> getSuggestedUsers(
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = UserContext.getCurrentUserId();
        limit = Math.min(Math.max(1, limit), 30);
        return ResponseEntity.ok(feedService.getSuggestedUsers(userId, limit));
    }

    @Operation(summary = "Hashtags thịnh hành (Trending Tags)")
    @GetMapping("/trending")
    public ResponseEntity<List<TrendingTagDto>> getTrendingTags(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(Math.max(1, limit), 50);
        return ResponseEntity.ok(feedService.getTrendingTags(limit));
    }
}
