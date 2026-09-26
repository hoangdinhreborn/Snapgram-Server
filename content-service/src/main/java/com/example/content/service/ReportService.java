package com.example.content.service;

import com.example.content.dto.CreateReportRequest;
import com.example.content.dto.ReportResponse;
import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.event.ModerationEvent;
import com.example.content.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                    .reportId(saved.getId().toString())
                    .reporterId(reporterId.toString())
                    .targetType(targetType.name())
                    .targetId(request.getTargetId().toString())
                    .reason(request.getReason())
                    .createdAt(saved.getCreatedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to publish moderation event: {}", e.getMessage());
        }

        return toResponse(saved);
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
