package com.example.media.repository;

import com.example.media.entity.MediaFile;
import com.example.media.entity.MediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Optional<MediaFile> findByIdAndStatusNot(UUID id, MediaStatus status);

    Page<MediaFile> findByOwnerIdAndStatusOrderByCreatedAtDesc(UUID ownerId, MediaStatus status, Pageable pageable);

    Page<MediaFile> findByOwnerIdAndStatusNotOrderByCreatedAtDesc(UUID ownerId, MediaStatus status, Pageable pageable);
}
