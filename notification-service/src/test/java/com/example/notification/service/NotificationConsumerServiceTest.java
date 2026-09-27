package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.ContentInteractionEvent;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @InjectMocks
    private NotificationConsumerService notificationConsumerService;

    @Test
    void shouldCreateLikeNotificationForTargetUser() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        notificationConsumerService = new NotificationConsumerService(notificationRepository, notificationDeliveryService, objectMapper);

        UUID actorId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        ContentInteractionEvent event = ContentInteractionEvent.builder()
                .userId(actorId.toString())
                .targetUserId(targetUserId.toString())
                .postId(postId.toString())
                .type("LIKE")
                .createdAt(Instant.now())
                .build();

        notificationConsumerService.handleLikeEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(targetUserId);
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getType()).isEqualTo(NotificationType.LIKE);
        assertThat(saved.getTargetType()).isEqualTo("POST");
        assertThat(saved.getTargetId()).isEqualTo(postId.toString());
        assertThat(saved.getPayload()).contains("postId");
        assertThat(saved.getPayload()).contains(actorId.toString());
    }
}
