package com.example.content.service;

import com.example.content.entity.Follow;
import com.example.content.entity.UserPrivacyView;
import com.example.content.event.FollowEvent;
import com.example.content.repository.FollowRepository;
import com.example.content.repository.UserPrivacyViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceEventTest {

    @Mock private FollowRepository followRepository;
    @Mock private UserPrivacyViewRepository userPrivacyViewRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void acceptedFollowPublishesStableFollowId() {
        FollowService followService = new FollowService(followRepository, userPrivacyViewRepository, kafkaTemplate);
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        when(followRepository.findByFollowerIdAndFollowingId(followerId, followingId)).thenReturn(Optional.empty());
        when(userPrivacyViewRepository.findById(followingId)).thenReturn(Optional.<UserPrivacyView>empty());
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
            Follow saved = invocation.getArgument(0);
            saved.setId(relationshipId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        followService.follow(followerId, followingId);

        ArgumentCaptor<FollowEvent> eventCaptor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(kafkaTemplate).send(eq("follow.events"), eq(followerId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(relationshipId.toString());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("ACCEPTED");
    }
}
