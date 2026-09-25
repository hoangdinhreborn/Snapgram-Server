package com.example.media.repository;

import com.example.media.entity.MediaMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaMetadataRepository extends JpaRepository<MediaMetadata, UUID> {
}
