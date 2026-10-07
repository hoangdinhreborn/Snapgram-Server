package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationPushOutbox;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.CallEvent;
import com.example.notification.event.ChatMessageEvent;
import com.example.notification.event.ContentInteractionEvent;
import com.example.notification.event.FollowEvent;
import com.example.notification.event.ModerationEvent;
import com.example.notification.event.MentionEvent;
import com.example.notification.event.StoryCreatedEvent;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.NotificationPushOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

        @Mock
        private NotificationPushOutboxRepository pushOutboxRepository;

        @Mock
        private NotificationEventProcessingService eventProcessingService;

    @InjectMocks
    private NotificationConsumerService notificationConsumerService;

    @Test
    void shouldCreateLikeNotificationForTargetUser() throws Exception {
        notificationConsumerService = newConsumer();
        Mockito.lenient().when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Mockito.lenient().when(pushOutboxRepository.save(org.mockito.ArgumentMatchers.any(NotificationPushOutbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

    @Test
    void shouldCreateStoryNotificationsForEachFollower() {
        notificationConsumerService = newConsumer();
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID authorId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID firstFollowerId = UUID.randomUUID();
        UUID secondFollowerId = UUID.randomUUID();
        StoryCreatedEvent event = StoryCreatedEvent.builder()
                .authorId(authorId.toString())
                .storyId(storyId.toString())
                .mediaUrl("https://example.test/story.jpg")
                .followerIds(List.of(firstFollowerId.toString(), secondFollowerId.toString()))
                .createdAt(Instant.now())
                .build();

        notificationConsumerService.handleStoryCreatedEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Notification::getUserId)
                .containsExactly(firstFollowerId, secondFollowerId);
        assertThat(captor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.getActorId()).isEqualTo(authorId);
                    assertThat(notification.getType()).isEqualTo(NotificationType.STORY_CREATED);
                    assertThat(notification.getTargetType()).isEqualTo("STORY");
                    assertThat(notification.getTargetId()).isEqualTo(storyId.toString());
                });
        ArgumentCaptor<NotificationPushOutbox> outboxCaptor = ArgumentCaptor.forClass(NotificationPushOutbox.class);
        verify(pushOutboxRepository, times(2)).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues()).allSatisfy(outbox -> assertThat(outbox.isGroupedUpdate()).isFalse());
    }

        @Test
        void shouldCreateMentionNotificationForMentionedUser() {
                notificationConsumerService = newConsumer();
                when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                UUID mentionedUserId = UUID.randomUUID();
                UUID authorId = UUID.randomUUID();
                UUID postId = UUID.randomUUID();
                MentionEvent event = MentionEvent.builder()
                                .mentionedUserId(mentionedUserId.toString())
                                .authorId(authorId.toString())
                                .postId(postId.toString())
                                .createdAt(Instant.now())
                                .build();

                notificationConsumerService.handleMentionEvent(event);

                ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
                verify(notificationRepository).save(captor.capture());
                Notification saved = captor.getValue();
                assertThat(saved.getUserId()).isEqualTo(mentionedUserId);
                assertThat(saved.getActorId()).isEqualTo(authorId);
                assertThat(saved.getType()).isEqualTo(NotificationType.MENTION);
                assertThat(saved.getTargetType()).isEqualTo("POST");
                assertThat(saved.getTargetId()).isEqualTo(postId.toString());
                assertThat(saved.getPayload()).contains(postId.toString(), authorId.toString());
                verify(pushOutboxRepository).save(org.mockito.ArgumentMatchers.any(NotificationPushOutbox.class));
        }

    @Test
    void shouldAggregateLikeNotificationsFromDifferentActors() {
        notificationConsumerService = newConsumer();
        UUID recipientId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID firstActorId = UUID.randomUUID();
        UUID secondActorId = UUID.randomUUID();

        Notification existing = new Notification();
        existing.setUserId(recipientId);
        existing.setType(NotificationType.LIKE);
        existing.setTargetType("POST");
        existing.setTargetId(postId.toString());
        existing.setActorId(firstActorId);
        existing.setPayload("{\"postId\":\"" + postId + "\",\"actorIds\":[\"" + firstActorId + "\"],\"count\":1}");
        existing.setCreatedAt(Instant.now());

        when(notificationRepository.findRecentUnreadGroup(
                org.mockito.ArgumentMatchers.eq(recipientId), org.mockito.ArgumentMatchers.eq(NotificationType.LIKE),
                org.mockito.ArgumentMatchers.eq("POST"), org.mockito.ArgumentMatchers.eq(postId.toString()),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(existing));
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationConsumerService.handleLikeEvent(interaction(firstActorId, recipientId, postId, "LIKE"));
        notificationConsumerService.handleLikeEvent(interaction(secondActorId, recipientId, postId, "LIKE"));

        assertThat(existing.getPayload()).contains(secondActorId.toString(), "\"count\":2");
        ArgumentCaptor<NotificationPushOutbox> outboxCaptor = ArgumentCaptor.forClass(NotificationPushOutbox.class);
        verify(pushOutboxRepository, times(2)).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues()).extracting(NotificationPushOutbox::isGroupedUpdate)
                .containsExactly(false, true);
    }

    @Test
    void shouldNotifyFolloweeOnlyForAcceptedFollow() {
        notificationConsumerService = newConsumer();
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();

        notificationConsumerService.handleFollowEvent(FollowEvent.builder()
                .followerId(followerId.toString()).followingId(followingId.toString()).status("PENDING").build());
        notificationConsumerService.handleFollowEvent(FollowEvent.builder()
                .followerId(followerId.toString()).followingId(followingId.toString()).status("ACCEPTED").build());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(followingId);
        assertThat(captor.getValue().getActorId()).isEqualTo(followerId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.FOLLOW);
    }

    @Test
        void shouldSkipMutedChatMessagesAndDeliverUnmutedMessages() {
        notificationConsumerService = newConsumer();
                when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        String conversationId = UUID.randomUUID().toString();

        notificationConsumerService.handleChatMessageEvent(ChatMessageEvent.builder()
                .senderId(senderId.toString()).receiverId(receiverId.toString()).conversationId(conversationId).muted(true).build());
        notificationConsumerService.handleChatMessageEvent(ChatMessageEvent.builder()
                .senderId(senderId.toString()).receiverId(receiverId.toString()).conversationId(conversationId).build());
        notificationConsumerService.handleChatMessageEvent(ChatMessageEvent.builder()
                .senderId(senderId.toString()).receiverId(receiverId.toString()).conversationId(conversationId).muted(false).build());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(receiverId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MESSAGE);
        verify(pushOutboxRepository).save(org.mockito.ArgumentMatchers.any(NotificationPushOutbox.class));
    }

    @Test
    void shouldNotifyReporterOnlyWhenReportIsResolved() {
        notificationConsumerService = newConsumer();
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID reporterId = UUID.randomUUID();
        String reportId = UUID.randomUUID().toString();

        notificationConsumerService.handleModerationEvent(ModerationEvent.builder()
                .reporterId(reporterId.toString()).reportId(reportId).status("PENDING").build());
        notificationConsumerService.handleModerationEvent(ModerationEvent.builder()
                .reporterId(reporterId.toString()).reportId(reportId).status("RESOLVED").build());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(reporterId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.REPORT_RESOLVED);
        assertThat(captor.getValue().getTargetId()).isEqualTo(reportId);
    }

    @Test
    void shouldNotifyCalleeOnlyForMissedCall() {
        notificationConsumerService = newConsumer();
        when(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID callerId = UUID.randomUUID();
        UUID calleeId = UUID.randomUUID();
        String callId = UUID.randomUUID().toString();

        notificationConsumerService.handleCallEvent(CallEvent.builder()
                .callerId(callerId.toString()).calleeId(calleeId.toString()).callId(callId).build());
        notificationConsumerService.handleCallEvent(CallEvent.builder()
                .callerId(callerId.toString()).calleeId(calleeId.toString()).callId(callId).missed(false).build());
        notificationConsumerService.handleCallEvent(CallEvent.builder()
                .callerId(callerId.toString()).calleeId(calleeId.toString()).callId(callId).missed(true).build());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(calleeId);
        assertThat(captor.getValue().getActorId()).isEqualTo(callerId);
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.MISSED_CALL);
        assertThat(captor.getValue().getTargetId()).isEqualTo(callId);
    }

        @Test
        void shouldUseEventIdFromEnvelopeAndDispatchNestedData() {
                notificationConsumerService = newConsumer();
                UUID eventId = UUID.randomUUID();
                UUID followerId = UUID.randomUUID();
                UUID followingId = UUID.randomUUID();
                String payload = """
                                {"eventId":"%s","schemaVersion":1,"data":{"followerId":"%s","followingId":"%s","status":"PENDING"}}
                                """.formatted(eventId, followerId, followingId);
                doAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(2)).run();
                        return null;
                }).when(eventProcessingService).process(eq("follow.events"), eq(eventId), any(Runnable.class));

                notificationConsumerService.consumeFollowEvent(new ConsumerRecord<>("follow.events", 2, 91L, "key", payload));

                verify(eventProcessingService).process(eq("follow.events"), eq(eventId), any(Runnable.class));
                verify(notificationRepository, Mockito.never()).save(any(Notification.class));
        }

        @Test
        void shouldUseStableKafkaCoordinatesWhenEventIdIsMissing() {
                notificationConsumerService = newConsumer();
                String payload = """
                                {"followerId":"%s","followingId":"%s","status":"PENDING"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID());
                UUID expectedFallbackId = UUID.nameUUIDFromBytes("follow.events:2:91".getBytes(java.nio.charset.StandardCharsets.UTF_8));

                notificationConsumerService.consumeFollowEvent(new ConsumerRecord<>("follow.events", 2, 91L, "key", payload));

                verify(eventProcessingService).process(eq("follow.events"), eq(expectedFallbackId), any(Runnable.class));
        }

    private ContentInteractionEvent interaction(UUID actorId, UUID recipientId, UUID postId, String type) {
        return ContentInteractionEvent.builder()
                .userId(actorId.toString())
                .targetUserId(recipientId.toString())
                .postId(postId.toString())
                .type(type)
                .createdAt(Instant.now())
                .build();
    }

        private NotificationConsumerService newConsumer() {
                return new NotificationConsumerService(
                                notificationRepository,
                                pushOutboxRepository,
                                new ObjectMapper(),
                                eventProcessingService);
        }
}
