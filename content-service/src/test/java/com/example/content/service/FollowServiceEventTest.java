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
    @Mock private com.example.content.client.AuthServiceClient authServiceClient;

    @Test
    void acceptedFollowPublishesStableFollowId() {
        FollowService followService = new FollowService(followRepository, userPrivacyViewRepository, kafkaTemplate, authServiceClient);
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

    @Test
    void unfollowPublishesUnfollowEvent() {
        FollowService followService = new FollowService(followRepository, userPrivacyViewRepository, kafkaTemplate, authServiceClient);
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();

        Follow follow = new Follow();
        follow.setId(relationshipId);
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);

        when(followRepository.findByFollowerIdAndFollowingId(followerId, followingId)).thenReturn(Optional.of(follow));

        followService.unfollow(followerId, followingId);

        verify(followRepository).delete(follow);

        ArgumentCaptor<FollowEvent> eventCaptor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(kafkaTemplate).send(eq("follow.events"), eq(followerId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(relationshipId.toString());
        assertThat(eventCaptor.getValue().getFollowerId()).isEqualTo(followerId.toString());
        assertThat(eventCaptor.getValue().getFollowingId()).isEqualTo(followingId.toString());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("UNFOLLOW");
    }

    @Test
    void rejectRequestPublishesRejectedEvent() {
        FollowService followService = new FollowService(followRepository, userPrivacyViewRepository, kafkaTemplate, authServiceClient);
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        UUID followId = UUID.randomUUID();

        Follow follow = new Follow();
        follow.setId(followId);
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);

        when(followRepository.findById(followId)).thenReturn(Optional.of(follow));

        followService.rejectRequest(followId, followingId);

        verify(followRepository).delete(follow);

        ArgumentCaptor<FollowEvent> eventCaptor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(kafkaTemplate).send(eq("follow.events"), eq(followerId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(followId.toString());
        assertThat(eventCaptor.getValue().getFollowerId()).isEqualTo(followerId.toString());
        assertThat(eventCaptor.getValue().getFollowingId()).isEqualTo(followingId.toString());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void removeMutualFollowDeletesBothAndPublishesUnfollowEvents() {
        FollowService followService = new FollowService(followRepository, userPrivacyViewRepository, kafkaTemplate, authServiceClient);
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        Follow followAB = new Follow();
        followAB.setId(UUID.randomUUID());
        followAB.setFollowerId(userA);
        followAB.setFollowingId(userB);

        Follow followBA = new Follow();
        followBA.setId(UUID.randomUUID());
        followBA.setFollowerId(userB);
        followBA.setFollowingId(userA);

        when(followRepository.findByFollowerIdAndFollowingId(userA, userB)).thenReturn(Optional.of(followAB));
        when(followRepository.findByFollowerIdAndFollowingId(userB, userA)).thenReturn(Optional.of(followBA));

        followService.removeMutualFollow(userA, userB);

        verify(followRepository).delete(followAB);
        verify(followRepository).delete(followBA);

        ArgumentCaptor<FollowEvent> eventCaptor = ArgumentCaptor.forClass(FollowEvent.class);
        verify(kafkaTemplate).send(eq("follow.events"), eq(userA.toString()), eventCaptor.capture());
        verify(kafkaTemplate).send(eq("follow.events"), eq(userB.toString()), eventCaptor.capture());

        assertThat(eventCaptor.getAllValues()).hasSize(2);
        assertThat(eventCaptor.getAllValues())
                .allMatch(e -> "UNFOLLOW".equals(e.getStatus()));
    }
}
