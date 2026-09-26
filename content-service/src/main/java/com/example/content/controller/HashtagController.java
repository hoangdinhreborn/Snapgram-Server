package com.example.content.controller;

import com.example.content.dto.HashtagResponse;
import com.example.content.dto.PostResponse;
import com.example.content.service.HashtagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hashtags")
@RequiredArgsConstructor
@Tag(name = "Hashtags API", description = "Tìm kiếm hashtag và bài đăng theo hashtag")
public class HashtagController {

    private final HashtagService hashtagService;

    @Operation(summary = "Tìm kiếm hashtag")
    @GetMapping("/search")
    public ResponseEntity<List<HashtagResponse>> searchHashtags(@RequestParam String q) {
        return ResponseEntity.ok(hashtagService.searchHashtags(q));
    }

    @Operation(summary = "Bài đăng theo hashtag")
    @GetMapping("/{tag}/posts")
    public ResponseEntity<Page<PostResponse>> getPostsByHashtag(
            @PathVariable String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(hashtagService.getPostsByHashtag(tag, PageRequest.of(page, size)));
    }
}
