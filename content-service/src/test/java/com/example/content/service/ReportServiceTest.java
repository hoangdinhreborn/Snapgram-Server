package com.example.content.service;

import com.example.content.dto.ReportResponse;
import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import com.example.content.exception.InvalidStatusTransitionException;
import com.example.content.repository.CommentRepository;
import com.example.content.repository.PostRepository;
import com.example.content.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ReportService reportService;

    private Report createSampleReport(ReportStatus status, ReportTargetType targetType) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporterId(UUID.randomUUID());
        report.setTargetType(targetType);
        report.setTargetId(UUID.randomUUID());
        report.setReason("Spam content");
        report.setDescription("Detailed description");
        report.setStatus(status);
        report.setCreatedAt(Instant.now());
        return report;
    }

    @Test
    @DisplayName("getReports with null status and null targetType should call findAll")
    void getReports_WhenBothNull_ShouldCallFindAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Report report = createSampleReport(ReportStatus.PENDING, ReportTargetType.USER);
        Page<Report> page = new PageImpl<>(List.of(report));

        when(reportRepository.findAll(pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.getReports(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo("USER");
        verify(reportRepository).findAll(pageable);
        verify(reportRepository, never()).findByStatus(any(), any());
        verify(reportRepository, never()).findByTargetType(any(), any());
        verify(reportRepository, never()).findByStatusAndTargetType(any(), any(), any());
    }

    @Test
    @DisplayName("getReports with status and null targetType should call findByStatus")
    void getReports_WhenStatusOnly_ShouldCallFindByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Report report = createSampleReport(ReportStatus.PENDING, ReportTargetType.POST);
        Page<Report> page = new PageImpl<>(List.of(report));

        when(reportRepository.findByStatus(ReportStatus.PENDING, pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.getReports(ReportStatus.PENDING, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDING");
        verify(reportRepository).findByStatus(ReportStatus.PENDING, pageable);
        verify(reportRepository, never()).findAll(pageable);
        verify(reportRepository, never()).findByTargetType(any(), any());
        verify(reportRepository, never()).findByStatusAndTargetType(any(), any(), any());
    }

    @Test
    @DisplayName("getReports with null status and targetType should call findByTargetType")
    void getReports_WhenTargetTypeOnly_ShouldCallFindByTargetType() {
        Pageable pageable = PageRequest.of(0, 10);
        Report report = createSampleReport(ReportStatus.REVIEWED, ReportTargetType.USER);
        Page<Report> page = new PageImpl<>(List.of(report));

        when(reportRepository.findByTargetType(ReportTargetType.USER, pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.getReports(null, ReportTargetType.USER, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo("USER");
        verify(reportRepository).findByTargetType(ReportTargetType.USER, pageable);
        verify(reportRepository, never()).findAll(pageable);
        verify(reportRepository, never()).findByStatus(any(), any());
        verify(reportRepository, never()).findByStatusAndTargetType(any(), any(), any());
    }

    @Test
    @DisplayName("getReports with both status and targetType should call findByStatusAndTargetType")
    void getReports_WhenBothProvided_ShouldCallFindByStatusAndTargetType() {
        Pageable pageable = PageRequest.of(0, 10);
        Report report = createSampleReport(ReportStatus.PENDING, ReportTargetType.COMMENT);
        Page<Report> page = new PageImpl<>(List.of(report));

        when(reportRepository.findByStatusAndTargetType(ReportStatus.PENDING, ReportTargetType.COMMENT, pageable))
                .thenReturn(page);

        Page<ReportResponse> result = reportService.getReports(ReportStatus.PENDING, ReportTargetType.COMMENT, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo("COMMENT");
        verify(reportRepository).findByStatusAndTargetType(ReportStatus.PENDING, ReportTargetType.COMMENT, pageable);
        verify(reportRepository, never()).findAll(pageable);
        verify(reportRepository, never()).findByStatus(any(), any());
        verify(reportRepository, never()).findByTargetType(any(), any());
    }

    @Test
    @DisplayName("resolveReport on DISMISSED report should throw InvalidStatusTransitionException")
    void resolveReport_WhenDismissed_ShouldThrowInvalidStatusTransition() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Report report = createSampleReport(ReportStatus.DISMISSED, ReportTargetType.POST);
        report.setId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(java.util.Optional.of(report));

        assertThatThrownBy(() -> reportService.resolveReport(reportId, adminId))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Dismissed report cannot be resolved");
    }

    @Test
    @DisplayName("resolveReport on already RESOLVED report should be idempotent")
    void resolveReport_WhenAlreadyResolved_ShouldBeIdempotent() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Report report = createSampleReport(ReportStatus.RESOLVED, ReportTargetType.POST);
        report.setId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(java.util.Optional.of(report));

        ReportResponse response = reportService.resolveReport(reportId, adminId);

        assertThat(response.getStatus()).isEqualTo("RESOLVED");
        verify(reportRepository, never()).save(any());
        verifyNoInteractions(postRepository);
        verifyNoInteractions(commentRepository);
    }

    @Test
    @DisplayName("resolveReport with null adminId should throw IllegalArgumentException")
    void resolveReport_WhenAdminIdNull_ShouldThrowIllegalArgument() {
        UUID reportId = UUID.randomUUID();

        assertThatThrownBy(() -> reportService.resolveReport(reportId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminId must not be null");
    }

    @Test
    @DisplayName("dismissReport on RESOLVED report should throw InvalidStatusTransitionException")
    void dismissReport_WhenResolved_ShouldThrowInvalidStatusTransition() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Report report = createSampleReport(ReportStatus.RESOLVED, ReportTargetType.POST);
        report.setId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(java.util.Optional.of(report));

        assertThatThrownBy(() -> reportService.dismissReport(reportId, adminId))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Resolved report cannot be dismissed");
    }

    @Test
    @DisplayName("dismissReport on already DISMISSED report should be idempotent")
    void dismissReport_WhenAlreadyDismissed_ShouldBeIdempotent() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Report report = createSampleReport(ReportStatus.DISMISSED, ReportTargetType.POST);
        report.setId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(java.util.Optional.of(report));

        ReportResponse response = reportService.dismissReport(reportId, adminId);

        assertThat(response.getStatus()).isEqualTo("DISMISSED");
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("dismissReport with null adminId should throw IllegalArgumentException")
    void dismissReport_WhenAdminIdNull_ShouldThrowIllegalArgument() {
        UUID reportId = UUID.randomUUID();

        assertThatThrownBy(() -> reportService.dismissReport(reportId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adminId must not be null");
    }

    @Test
    @DisplayName("dismissReport on PENDING report should set DISMISSED and save")
    void dismissReport_WhenPending_ShouldDismissAndSave() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Report report = createSampleReport(ReportStatus.PENDING, ReportTargetType.POST);
        report.setId(reportId);

        when(reportRepository.findById(reportId)).thenReturn(java.util.Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.dismissReport(reportId, adminId);

        assertThat(response.getStatus()).isEqualTo("DISMISSED");
        verify(reportRepository).save(report);
    }
}
