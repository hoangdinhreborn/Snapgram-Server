package com.example.content.service;

import com.example.content.dto.CreateReportRequest;
import com.example.content.dto.ReportResponse;
import com.example.content.entity.Comment;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.event.EventIds;
import com.example.content.event.ModerationEvent;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.exception.InvalidStatusTransitionException;
import com.example.content.repository.CommentRepository;
import com.example.content.repository.PostRepository;
import com.example.content.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private static final String TOPIC_MODERATION = "moderation.events";

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.example.content.client.AuthServiceClient authServiceClient;

    @Transactional
    public ReportResponse createReport(UUID reporterId, CreateReportRequest request) {
        ReportTargetType targetType;
        try {
            targetType = ReportTargetType.valueOf(request.getTargetType());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid targetType: " + request.getTargetType());
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus(ReportStatus.PENDING);
        Report saved = reportRepository.save(report);

        try {
            kafkaTemplate.send(TOPIC_MODERATION, saved.getId().toString(), ModerationEvent.builder()
                    .eventId(EventIds.stableFor(TOPIC_MODERATION, saved.getId().toString(), ReportStatus.PENDING.name()))
                    .reportId(saved.getId().toString())
                    .reporterId(reporterId.toString())
                    .targetType(targetType.name())
                    .targetId(request.getTargetId().toString())
                    .reason(request.getReason())
                    .status(ReportStatus.PENDING.name())
                    .createdAt(saved.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish moderation event: {}", e.getMessage());
        }

        return toResponse(saved);
    }

    /**
     * Admin: Get all reports with optional status and targetType filters.
     */
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReports(ReportStatus status, ReportTargetType targetType, Pageable pageable) {
        Page<Report> page;
        if (status != null && targetType != null) {
            page = reportRepository.findByStatusAndTargetType(status, targetType, pageable);
        } else if (status != null) {
            page = reportRepository.findByStatus(status, pageable);
        } else if (targetType != null) {
            page = reportRepository.findByTargetType(targetType, pageable);
        } else {
            page = reportRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Admin: Get report detail by ID.
     */
    @Transactional(readOnly = true)
    public ReportResponse getReportById(UUID id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Report not found: " + id));
        return toResponse(report);
    }

    /**
     * Admin: Resolve a report (hide/delete offending content).
     */
    @Transactional
    public ReportResponse resolveReport(UUID id, UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null");
        }
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Report not found: " + id));

        if (report.getStatus() == ReportStatus.RESOLVED) {
            log.info("Report {} is already resolved; idempotent return", id);
            return toResponse(report);
        }
        if (report.getStatus() == ReportStatus.DISMISSED) {
            throw new InvalidStatusTransitionException("Dismissed report cannot be resolved");
        }

        String targetOwnerId = null;
        if (report.getTargetType() == ReportTargetType.POST) {
            var postOpt = postRepository.findById(report.getTargetId());
            if (postOpt.isPresent()) {
                Post post = postOpt.get();
                targetOwnerId = post.getAuthorId().toString();
                post.setStatus(PostStatus.DELETED);
                postRepository.save(post);
                log.info("Post {} marked as DELETED by admin {} due to report {}", post.getId(), adminId, id);
            }
        } else if (report.getTargetType() == ReportTargetType.COMMENT) {
            var commentOpt = commentRepository.findById(report.getTargetId());
            if (commentOpt.isPresent()) {
                Comment comment = commentOpt.get();
                targetOwnerId = comment.getAuthorId().toString();
                commentRepository.delete(comment);
                log.info("Comment {} deleted by admin {} due to report {}", comment.getId(), adminId, id);
            }
        } else if (report.getTargetType() == ReportTargetType.USER) {
            targetOwnerId = report.getTargetId().toString();
            try {
                String banReason = "Báo cáo vi phạm tiêu chuẩn cộng đồng: " + report.getReason();
                authServiceClient.banUser(report.getTargetId(), banReason);
                log.info("User {} auto-banned due to resolved report {}", report.getTargetId(), id);
            } catch (Exception ex) {
                log.warn("Failed to auto-ban user {} on report resolution: {}", report.getTargetId(), ex.getMessage());
            }
        }

        report.setStatus(ReportStatus.RESOLVED);
        Report updated = reportRepository.save(report);
        try {
            kafkaTemplate.send(TOPIC_MODERATION, updated.getId().toString(), ModerationEvent.builder()
                    .eventId(EventIds.stableFor(TOPIC_MODERATION, updated.getId().toString(), ReportStatus.RESOLVED.name()))
                    .reportId(updated.getId().toString())
                    .reporterId(updated.getReporterId().toString())
                    .targetType(updated.getTargetType().name())
                    .targetId(updated.getTargetId().toString())
                    .targetOwnerId(targetOwnerId)
                    .reason(updated.getReason())
                    .status(ReportStatus.RESOLVED.name())
                    .moderatorId(adminId.toString())
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish resolved moderation event: {}", e.getMessage());
        }
        log.info("Report {} resolved by admin {}", id, adminId);
        return toResponse(updated);
    }

    /**
     * Admin: Dismiss a report (report deemed invalid / not violating policies).
     */
    @Transactional
    public ReportResponse dismissReport(UUID id, UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId must not be null");
        }
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Report not found: " + id));

        if (report.getStatus() == ReportStatus.DISMISSED) {
            log.info("Report {} is already dismissed; idempotent return", id);
            return toResponse(report);
        }
        if (report.getStatus() == ReportStatus.RESOLVED) {
            throw new InvalidStatusTransitionException("Resolved report cannot be dismissed");
        }

        report.setStatus(ReportStatus.DISMISSED);
        Report updated = reportRepository.save(report);
        log.info("Report {} dismissed by admin {}", id, adminId);
        return toResponse(updated);
    }

    private ReportResponse toResponse(Report r) {
        ReportResponse.ReportResponseBuilder builder = ReportResponse.builder()
                .id(r.getId().toString())
                .reporterId(r.getReporterId().toString())
                .targetType(r.getTargetType().name())
                .targetId(r.getTargetId().toString())
                .reason(r.getReason())
                .description(r.getDescription())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt());

        if (r.getTargetType() == ReportTargetType.POST) {
            postRepository.findById(r.getTargetId()).ifPresent(post -> {
                builder.targetContent(post.getCaption())
                       .targetStatus(post.getStatus() != null ? post.getStatus().name() : null)
                       .targetCreatedAt(post.getCreatedAt())
                       .targetAuthorId(post.getAuthorId().toString());

                java.util.List<String> mediaUrls = new java.util.ArrayList<>();
                if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()) {
                    mediaUrls.add(post.getMediaUrl());
                }
                if (post.getMediaList() != null) {
                    for (var m : post.getMediaList()) {
                        if (m.getMediaUrl() != null && !m.getMediaUrl().isBlank() && !mediaUrls.contains(m.getMediaUrl())) {
                            mediaUrls.add(m.getMediaUrl());
                        }
                    }
                }
                builder.targetMediaUrls(mediaUrls);

                try {
                    var author = authServiceClient.getUserSummary(post.getAuthorId());
                    if (author != null) {
                        builder.targetAuthorUsername(author.getUsername())
                               .targetAuthorDisplayName(author.getDisplayName())
                               .targetAuthorAvatarUrl(author.getAvatarUrl());
                    }
                } catch (Exception ignored) {}
            });
        } else if (r.getTargetType() == ReportTargetType.COMMENT) {
            commentRepository.findById(r.getTargetId()).ifPresent(comment -> {
                builder.targetContent(comment.getContent())
                       .targetStatus("ACTIVE")
                       .targetCreatedAt(comment.getCreatedAt())
                       .targetAuthorId(comment.getAuthorId().toString());

                if (comment.getMediaUrl() != null && !comment.getMediaUrl().isBlank()) {
                    builder.targetMediaUrls(java.util.List.of(comment.getMediaUrl()));
                }

                try {
                    var author = authServiceClient.getUserSummary(comment.getAuthorId());
                    if (author != null) {
                        builder.targetAuthorUsername(author.getUsername())
                               .targetAuthorDisplayName(author.getDisplayName())
                               .targetAuthorAvatarUrl(author.getAvatarUrl());
                    }
                } catch (Exception ignored) {}
            });
        } else if (r.getTargetType() == ReportTargetType.USER) {
            try {
                var author = authServiceClient.getUserSummary(r.getTargetId());
                if (author != null) {
                    builder.targetAuthorId(r.getTargetId().toString())
                           .targetAuthorUsername(author.getUsername())
                           .targetAuthorDisplayName(author.getDisplayName())
                           .targetAuthorAvatarUrl(author.getAvatarUrl())
                           .targetStatus("ACTIVE");
                }
            } catch (Exception ignored) {}
        }

        return builder.build();
    }
}
