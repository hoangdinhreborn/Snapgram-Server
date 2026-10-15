package com.example.notification.service;

import com.example.notification.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventProcessingService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void process(String topic, UUID eventId, Runnable handler) {
        int claimed = processedEventRepository.claim(UUID.randomUUID(), topic, eventId);
        if (claimed == 0) {
            log.info("Ignoring duplicate Kafka event topic={} eventId={}", topic, eventId);
            return;
        }

        handler.run();
    }
}