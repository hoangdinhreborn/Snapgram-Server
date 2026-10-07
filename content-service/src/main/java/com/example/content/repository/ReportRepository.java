package com.example.content.repository;

import com.example.content.entity.Report;
import com.example.content.entity.ReportStatus;
import com.example.content.entity.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    Page<Report> findByStatusAndTargetType(ReportStatus status, ReportTargetType targetType, Pageable pageable);
}
