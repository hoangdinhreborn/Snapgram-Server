package com.example.notification.service;

import com.example.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventProcessingServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private NotificationEventProcessingService eventProcessingService;

    @Test
    void duplicateEventDoesNotRunHandler() {
        String topic = "follow.events";
        UUID eventId = UUID.randomUUID();
        AtomicBoolean handlerRan = new AtomicBoolean();
        when(processedEventRepository.claim(any(), eq(topic), eq(eventId))).thenReturn(0);

        eventProcessingService.process(topic, eventId, () -> handlerRan.set(true));

        assertThat(handlerRan).isFalse();
    }

    @Test
    void newlyClaimedEventRunsHandler() {
        String topic = "follow.events";
        UUID eventId = UUID.randomUUID();
        AtomicBoolean handlerRan = new AtomicBoolean();
        when(processedEventRepository.claim(any(), eq(topic), eq(eventId))).thenReturn(1);

        eventProcessingService.process(topic, eventId, () -> handlerRan.set(true));

        assertThat(handlerRan).isTrue();
    }

    @Test
    void handlerFailurePropagatesSoKafkaCanRetry() {
        String topic = "follow.events";
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.claim(any(), eq(topic), eq(eventId))).thenReturn(1);
        IllegalStateException failure = new IllegalStateException("database unavailable");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> eventProcessingService.process(topic, eventId, () -> { throw failure; }))
                .isSameAs(failure);
    }
}
