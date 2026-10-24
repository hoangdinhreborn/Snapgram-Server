package com.example.content.service;

import com.example.content.entity.UserPrivacyView;
import com.example.content.repository.UserPrivacyViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private UserPrivacyViewRepository userPrivacyViewRepository;

    @Mock
    private FollowService followService;

    @InjectMocks
    private UserEventConsumer userEventConsumer;

    @Test
    void handleUserEvent_UserBlocked_CallsRemoveMutualFollow() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "eventType", "USER_BLOCKED",
                "blockerId", blockerId.toString(),
                "blockedId", blockedId.toString()
        );

        userEventConsumer.handleUserEvent(payload);

        verify(followService).removeMutualFollow(blockerId, blockedId);
        verifyNoInteractions(userPrivacyViewRepository);
    }

    @Test
    void handleUserEvent_PrivacyChanged_UpdatesPrivacy() {
        UUID userId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "eventType", "PRIVACY_CHANGED",
                "userId", userId.toString(),
                "isPrivate", true
        );

        when(userPrivacyViewRepository.findById(userId)).thenReturn(Optional.empty());

        userEventConsumer.handleUserEvent(payload);

        ArgumentCaptor<UserPrivacyView> captor = ArgumentCaptor.forClass(UserPrivacyView.class);
        verify(userPrivacyViewRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().isPrivate()).isTrue();
        verifyNoInteractions(followService);
    }
}
