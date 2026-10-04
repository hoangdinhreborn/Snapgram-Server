package com.example.content.service;

import com.example.content.dto.CreateReportRequest;
import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.event.EventIds;
import com.example.content.event.ModerationEvent;
import com.example.content.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceEventTest {

    @Mock private ReportRepository reportRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void reportCreationPublishesStablePendingEvent() {
        ReportService reportService = new ReportService(reportRepository, kafkaTemplate);
        UUID reportId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report saved = invocation.getArgument(0);
            saved.setId(reportId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetType("POST");
        request.setTargetId(targetId);
        request.setReason("SPAM");

        reportService.createReport(reporterId, request);

        ArgumentCaptor<ModerationEvent> eventCaptor = ArgumentCaptor.forClass(ModerationEvent.class);
        verify(kafkaTemplate).send(eq("moderation.events"), eq(reportId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId())
                .isEqualTo(EventIds.stableFor("moderation.events", reportId.toString(), "PENDING"));
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(eventCaptor.getValue().getReporterId()).isEqualTo(reporterId.toString());
    }

    @Test
    void resolvingReportPublishesResolvedEventWithModerator() {
        ReportService reportService = new ReportService(reportRepository, kafkaTemplate);
        UUID reportId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Report pendingReport = report(reportId, reporterId, ReportStatus.PENDING);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(pendingReport));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reportService.resolveReport(reportId, moderatorId);

        ArgumentCaptor<ModerationEvent> eventCaptor = ArgumentCaptor.forClass(ModerationEvent.class);
        verify(kafkaTemplate).send(eq("moderation.events"), eq(reportId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId())
                .isEqualTo(EventIds.stableFor("moderation.events", reportId.toString(), "RESOLVED"));
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(eventCaptor.getValue().getModeratorId()).isEqualTo(moderatorId.toString());
        assertThat(pendingReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    void resolvingAlreadyResolvedReportDoesNotRepublishEvent() {
        ReportService reportService = new ReportService(reportRepository, kafkaTemplate);
        UUID reportId = UUID.randomUUID();
        Report resolvedReport = report(reportId, UUID.randomUUID(), ReportStatus.RESOLVED);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(resolvedReport));

        reportService.resolveReport(reportId, UUID.randomUUID());

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    private Report report(UUID reportId, UUID reporterId, ReportStatus status) {
        Report report = new Report();
        report.setId(reportId);
        report.setReporterId(reporterId);
        report.setTargetType(ReportTargetType.POST);
        report.setTargetId(UUID.randomUUID());
        report.setReason("SPAM");
        report.setStatus(status);
        report.setCreatedAt(Instant.now());
        return report;
    }
}
