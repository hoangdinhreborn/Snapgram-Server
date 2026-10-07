package com.example.notification.service;

import com.example.notification.dto.DeviceTokenRequest;
import com.example.notification.entity.DeviceToken;
import com.example.notification.entity.Platform;
import com.example.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceToken saveToken(UUID userId, DeviceTokenRequest request) {
        Platform platform = request.getPlatform() != null ? request.getPlatform() : Platform.WEB;
        Optional<DeviceToken> existing = deviceTokenRepository.findByUserIdAndToken(userId, request.getToken());

        if (existing.isPresent()) {
            DeviceToken token = existing.get();
            token.setPlatform(platform);
            token.setLastSeenAt(Instant.now());
            return deviceTokenRepository.save(token);
        }

        DeviceToken token = new DeviceToken();
        token.setUserId(userId);
        token.setToken(request.getToken());
        token.setPlatform(platform);
        token.setLastSeenAt(Instant.now());
        return deviceTokenRepository.save(token);
    }
}
