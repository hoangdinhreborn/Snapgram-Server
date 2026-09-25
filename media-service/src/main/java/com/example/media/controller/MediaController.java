package com.example.media.controller;

import com.example.media.dto.MediaResponse;
import com.example.media.dto.PresignedUrlRequest;
import com.example.media.dto.PresignedUrlResponse;
import com.example.media.service.MediaService;
import com.example.media.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media API", description = "Quản lý upload, xử lý và truy xuất media")
public class MediaController {

    private final MediaService mediaService;

    /**
     * Upload ảnh/video trực tiếp (Multipart Form Data).
     * Tự động sinh thumbnail cho ảnh.
     */
    @Operation(summary = "Direct Upload (Multipart)", description = "Upload file trực tiếp lên server, tự động lưu MinIO và sinh thumbnail nếu là ảnh")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(
            @RequestPart("file") MultipartFile file) {
        UUID currentUserId = UserContext.getCurrentUserId();
        MediaResponse response = mediaService.upload(currentUserId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Tạo Presigned PUT URL để client tự upload file lớn trực tiếp lên MinIO.
     */
    @Operation(summary = "Tạo Presigned URL", description = "Tạo URL PUT để client upload trực tiếp file lớn lên MinIO mà không nghẽn server")
    @PostMapping("/presigned")
    public ResponseEntity<PresignedUrlResponse> createPresignedUpload(
            @Valid @RequestBody PresignedUrlRequest request) {
        UUID currentUserId = UserContext.getCurrentUserId();
        PresignedUrlResponse response = mediaService.createPresignedUpload(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Xác nhận upload thành công cho luồng Presigned URL.
     */
    @Operation(summary = "Xác nhận Presigned Upload", description = "Client gọi sau khi đã PUT file thành công lên MinIO bằng presigned URL")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<MediaResponse> confirmPresignedUpload(
            @PathVariable UUID id) {
        UUID currentUserId = UserContext.getCurrentUserId();
        MediaResponse response = mediaService.confirmPresignedUpload(currentUserId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Lấy thông tin chi tiết của 1 media file.
     */
    @Operation(summary = "Lấy chi tiết media", description = "Xem thông tin URL, thumbnail, kích thước của media")
    @GetMapping("/{id}")
    public ResponseEntity<MediaResponse> getMedia(@PathVariable UUID id) {
        return ResponseEntity.ok(mediaService.getMedia(id));
    }

    /**
     * Lấy danh sách media của chính người dùng hiện tại (phân trang).
     */
    @Operation(summary = "Danh sách media của tôi", description = "Lấy danh sách các media do user hiện tại tải lên")
    @GetMapping("/me")
    public ResponseEntity<Page<MediaResponse>> getMyMedia(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID currentUserId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(mediaService.getMyMedia(currentUserId, pageable));
    }

    /**
     * Xóa 1 media file (xóa trên MinIO và đánh dấu DELETED trong DB).
     */
    @Operation(summary = "Xóa media", description = "Xóa file khỏi MinIO và cập nhật trạng thái DELETED")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMedia(@PathVariable UUID id) {
        UUID currentUserId = UserContext.getCurrentUserId();
        mediaService.deleteMedia(currentUserId, id);
        return ResponseEntity.noContent().build();
    }
}
