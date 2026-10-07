package com.example.content.service;

import com.example.content.dto.CreateReportRequest;
import com.example.content.dto.ReportResponse;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.event.EventIds;
import com.example.content.event.ModerationEvent;
import com.example.content.exception.ContentNotFoundException;
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
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Report not found: " + id));

        if (report.getStatus() == ReportStatus.RESOLVED) {
            return toResponse(report);
        }
        if (report.getStatus() == ReportStatus.DISMISSED) {
            throw new IllegalArgumentException("Dismissed report cannot be resolved");
        }
        if (adminId == null) {
            throw new IllegalArgumentException("adminId is required");
        }

        if (report.getTargetType() == ReportTargetType.POST) {
            postRepository.findById(report.getTargetId()).ifPresent(post -> {
                post.setStatus(PostStatus.DELETED);
                postRepository.save(post);
                log.info("Post {} marked as DELETED by admin {} due to report {}", post.getId(), adminId, id);
            });
        } else if (report.getTargetType() == ReportTargetType.COMMENT) {
            commentRepository.findById(report.getTargetId()).ifPresent(comment -> {
                commentRepository.delete(comment);
                log.info("Comment {} deleted by admin {} due to report {}", comment.getId(), adminId, id);
            });
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
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("Report not found: " + id));

        report.setStatus(ReportStatus.DISMISSED);
        Report updated = reportRepository.save(report);
        log.info("Report {} dismissed by admin {}", id, adminId);
        return toResponse(updated);
    }

    private ReportResponse toResponse(Report r) {
        return ReportResponse.builder()
                .id(r.getId().toString())
                .reporterId(r.getReporterId().toString())
                .targetType(r.getTargetType().name())
                .targetId(r.getTargetId().toString())
                .reason(r.getReason())
                .description(r.getDescription())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
