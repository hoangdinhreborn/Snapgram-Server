package com.example.media.service;

import com.example.media.dto.MediaResponse;
import com.example.media.dto.PresignedUrlRequest;
import com.example.media.dto.PresignedUrlResponse;
import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaMetadata;
import com.example.media.entity.MediaStatus;
import com.example.media.event.MediaUploadedEvent;
import com.example.media.exception.InvalidMediaException;
import com.example.media.exception.MediaNotFoundException;
import com.example.media.exception.UnauthorizedException;
import com.example.media.repository.MediaFileRepository;
import com.example.media.repository.MediaMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MediaService {

    private static final String TOPIC_MEDIA_UPLOADED = "media.uploaded";

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4", "video/quicktime", "video/webm"
    );

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/webm", "webm"
    );

    private static final int PRESIGNED_EXPIRY_SECONDS = 900; // 15 minutes

    private final MediaFileRepository mediaFileRepository;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final StorageService storageService;
    private final ImageProcessingService imageProcessingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─────────────────────────────────────────────────────────────
    // Direct Upload (Multipart)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public MediaResponse upload(UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("File is empty or missing");
        }

        String contentType = file.getContentType();
        if (contentType == null || !isSupportedType(contentType)) {
            throw new InvalidMediaException("Unsupported media type: " + contentType);
        }

        try {
            byte[] fileBytes = file.getBytes();
            long size = file.getSize();
            String extension = EXTENSION_MAP.getOrDefault(contentType, "bin");
            String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fileId = UUID.randomUUID().toString();
            String objectKey = "users/" + ownerId + "/" + datePrefix + "/" + fileId + "." + extension;

            // 1. Upload original file
            storageService.upload(objectKey, new ByteArrayInputStream(fileBytes), size, contentType);
            String fileUrl = storageService.buildPublicUrl(objectKey);

            // 2. Handle Thumbnail and Metadata
            String thumbUrl = null;
            Integer width = null;
            Integer height = null;

            if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
                ImageProcessingService.Dimensions dims = imageProcessingService.extractDimensions(fileBytes);
                width = dims.width();
                height = dims.height();

                byte[] thumbBytes = imageProcessingService.generateThumbnail(fileBytes);
                if (thumbBytes != null) {
                    String thumbKey = "users/" + ownerId + "/" + datePrefix + "/" + fileId + "_thumb.jpg";
                    storageService.upload(thumbKey, new ByteArrayInputStream(thumbBytes), thumbBytes.length, "image/jpeg");
                    thumbUrl = storageService.buildPublicUrl(thumbKey);
                }
            }

            // 3. Persist entity
            MediaFile mediaFile = new MediaFile();
            mediaFile.setOwnerId(ownerId);
            mediaFile.setBucket(storageService.getBucket());
            mediaFile.setObjectKey(objectKey);
            mediaFile.setMimeType(contentType);
            mediaFile.setSizeBytes(size);
            mediaFile.setUrl(fileUrl);
            mediaFile.setThumbnailUrl(thumbUrl);
            mediaFile.setStatus(MediaStatus.READY);

            MediaFile saved = mediaFileRepository.save(mediaFile);

            // 4. Save metadata if present
            if (width != null || height != null) {
                MediaMetadata meta = new MediaMetadata();
                meta.setMediaFile(saved);
                meta.setWidth(width);
                meta.setHeight(height);
                meta.setExifStripped(true);
                mediaMetadataRepository.save(meta);
                saved.setMetadata(meta);
            }

            log.info("Media created successfully: id={}, ownerId={}", saved.getId(), ownerId);
            MediaResponse response = toResponse(saved);

            // 5. Publish Kafka event
            publishMediaUploadedEvent(saved, width, height);

            return response;

        } catch (Exception e) {
            log.error("Failed to process media upload: {}", e.getMessage(), e);
            throw new RuntimeException("Media upload processing failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Presigned URL Upload (For large files / direct client upload)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public PresignedUrlResponse createPresignedUpload(UUID ownerId, PresignedUrlRequest request) {
        String contentType = request.getMimeType();
        if (!isSupportedType(contentType)) {
            throw new InvalidMediaException("Unsupported media type: " + contentType);
        }

        String extension = EXTENSION_MAP.getOrDefault(contentType, "bin");
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fileId = UUID.randomUUID().toString();
        String objectKey = "users/" + ownerId + "/" + datePrefix + "/" + fileId + "." + extension;

        String uploadUrl = storageService.generatePresignedUploadUrl(objectKey, contentType, PRESIGNED_EXPIRY_SECONDS);
        String publicFileUrl = storageService.buildPublicUrl(objectKey);

        MediaFile mediaFile = new MediaFile();
        mediaFile.setOwnerId(ownerId);
        mediaFile.setBucket(storageService.getBucket());
        mediaFile.setObjectKey(objectKey);
        mediaFile.setMimeType(contentType);
        mediaFile.setSizeBytes(request.getSizeBytes());
        mediaFile.setUrl(publicFileUrl);
        mediaFile.setStatus(MediaStatus.UPLOADING);

        MediaFile saved = mediaFileRepository.save(mediaFile);

        return PresignedUrlResponse.builder()
                .mediaId(saved.getId().toString())
                .uploadUrl(uploadUrl)
                .fileUrl(publicFileUrl)
                .expiresInSeconds((long) PRESIGNED_EXPIRY_SECONDS)
                .build();
    }

    @Transactional
    public MediaResponse confirmPresignedUpload(UUID ownerId, UUID mediaId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found"));

        if (!mediaFile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedException("You do not have permission to modify this media");
        }

        if (mediaFile.getStatus() == MediaStatus.UPLOADING) {
            mediaFile.setStatus(MediaStatus.READY);
            mediaFileRepository.save(mediaFile);
            // Publish event after confirmation
            publishMediaUploadedEvent(mediaFile, null, null);
        }

        return toResponse(mediaFile);
    }

    // ─────────────────────────────────────────────────────────────
    // Query & Delete
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MediaResponse getMedia(UUID id) {
        MediaFile mediaFile = mediaFileRepository.findByIdAndStatusNot(id, MediaStatus.DELETED)
                .orElseThrow(() -> new MediaNotFoundException("Media not found"));
        return toResponse(mediaFile);
    }

    @Transactional(readOnly = true)
    public Page<MediaResponse> getMyMedia(UUID ownerId, Pageable pageable) {
        return mediaFileRepository
                .findByOwnerIdAndStatusNotOrderByCreatedAtDesc(ownerId, MediaStatus.DELETED, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void deleteMedia(UUID ownerId, UUID mediaId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found"));

        if (!mediaFile.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedException("You do not have permission to delete this media");
        }

        // Delete from MinIO
        storageService.delete(mediaFile.getObjectKey());
        if (mediaFile.getThumbnailUrl() != null) {
            String thumbKey = mediaFile.getObjectKey().replaceFirst("\\.[^.]+$", "_thumb.jpg");
            storageService.delete(thumbKey);
        }

        // Mark as DELETED in database
        mediaFile.setStatus(MediaStatus.DELETED);
        mediaFileRepository.save(mediaFile);
        log.info("Media marked as DELETED: id={}, ownerId={}", mediaId, ownerId);
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private void publishMediaUploadedEvent(MediaFile mediaFile, Integer width, Integer height) {
        try {
            Integer w = width;
            Integer h = height;
            if (w == null && mediaFile.getMetadata() != null) {
                w = mediaFile.getMetadata().getWidth();
                h = mediaFile.getMetadata().getHeight();
            }
            MediaUploadedEvent event = MediaUploadedEvent.builder()
                    .mediaId(mediaFile.getId().toString())
                    .ownerId(mediaFile.getOwnerId().toString())
                    .mimeType(mediaFile.getMimeType())
                    .url(mediaFile.getUrl())
                    .thumbnailUrl(mediaFile.getThumbnailUrl())
                    .sizeBytes(mediaFile.getSizeBytes())
                    .width(w)
                    .height(h)
                    .uploadedAt(mediaFile.getCreatedAt())
                    .build();
            kafkaTemplate.send(TOPIC_MEDIA_UPLOADED, mediaFile.getId().toString(), event);
            log.debug("Published MediaUploadedEvent for mediaId={}", mediaFile.getId());
        } catch (Exception e) {
            log.warn("Failed to publish MediaUploadedEvent for mediaId={}: {}", mediaFile.getId(), e.getMessage());
        }
    }

    private boolean isSupportedType(String mimeType) {
        return ALLOWED_IMAGE_TYPES.contains(mimeType) || ALLOWED_VIDEO_TYPES.contains(mimeType);
    }

    private MediaResponse toResponse(MediaFile m) {
        Integer width = null;
        Integer height = null;
        Integer duration = null;

        if (m.getMetadata() != null) {
            width = m.getMetadata().getWidth();
            height = m.getMetadata().getHeight();
            duration = m.getMetadata().getDurationSec();
        }

        return MediaResponse.builder()
                .id(m.getId().toString())
                .ownerId(m.getOwnerId().toString())
                .url(m.getUrl())
                .thumbnailUrl(m.getThumbnailUrl())
                .mimeType(m.getMimeType())
                .sizeBytes(m.getSizeBytes())
                .status(m.getStatus().name())
                .width(width)
                .height(height)
                .durationSec(duration)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
