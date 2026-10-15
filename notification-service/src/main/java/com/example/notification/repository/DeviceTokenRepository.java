package com.example.notification.repository;

import com.example.notification.entity.DeviceToken;
import com.example.notification.entity.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByUserIdAndToken(UUID userId, String token);

    List<DeviceToken> findByUserId(UUID userId);

    Optional<DeviceToken> findByUserIdAndPlatform(UUID userId, Platform platform);
}
