package com.example.media.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media/ai")
@RequiredArgsConstructor
@Tag(name = "AI Media API", description = "Các tính năng trí tuệ nhân tạo hỗ trợ nội dung đa phương tiện")
public class AiCaptionController {

    private final AiCaptionService aiCaptionService;

    @Operation(
            summary = "Gợi ý Caption, Hashtag & Alt-Text (AI Vision)",
            description = "Phân tích ảnh hoặc khung hình video để gợi ý 3 caption, 5-8 hashtag và alt-text theo phong cách (Tone)."
    )
    @PostMapping(value = "/caption", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiCaptionResponse> generateCaption(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "FRIENDLY", required = false) CaptionTone tone,
            @RequestParam(defaultValue = "IMAGE", required = false) MediaVisualType mediaType
    ) {
        AiCaptionResponse response = aiCaptionService.generateSuggestions(file, tone, mediaType);
        return ResponseEntity.ok(response);
    }
}
