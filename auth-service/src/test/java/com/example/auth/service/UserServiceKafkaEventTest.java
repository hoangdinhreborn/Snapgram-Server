package com.example.auth.service;

import com.example.auth.dto.user.PrivacySettingRequest;
import com.example.auth.entity.AuthUser;
import com.example.auth.repository.AuthBlockRepository;
import com.example.auth.repository.AuthNoteRepository;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.repository.AuthUserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceKafkaEventTest {

    @Mock private AuthUserRepository userRepository;
    @Mock private AuthUserRoleRepository userRoleRepository;
    @Mock private AuthBlockRepository blockRepository;
    @Mock private AuthNoteRepository noteRepository;
    @Mock private AuthRefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("updatePrivacy should save user and publish PRIVACY_CHANGED to user.events")
    @SuppressWarnings("unchecked")
    void updatePrivacy_ShouldPublishKafkaEvent() {
        UUID userId = UUID.randomUUID();
        AuthUser user = new AuthUser();
        user.setId(userId);
        user.setUsername("testuser");
        user.setPrivateAccount(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrivacySettingRequest request = new PrivacySettingRequest();
        request.setPrivateAccount(true);

        userService.updatePrivacy(userId, request);

        assertThat(user.isPrivateAccount()).isTrue();
        verify(userRepository).save(user);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("user.events"), eq(userId.toString()), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("PRIVACY_CHANGED");
        assertThat(payload.get("userId")).isEqualTo(userId.toString());
        assertThat(payload.get("isPrivate")).isEqualTo(true);
        assertThat(payload.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("blockUser should save block and publish USER_BLOCKED to user.events")
    @SuppressWarnings("unchecked")
    void blockUser_ShouldPublishKafkaEvent() {
        UUID blockerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(userRepository.existsById(targetId)).thenReturn(true);
        when(blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)).thenReturn(false);

        userService.blockUser(blockerId, targetId);

        verify(blockRepository).save(any());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("user.events"), eq(blockerId.toString()), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("USER_BLOCKED");
        assertThat(payload.get("blockerId")).isEqualTo(blockerId.toString());
        assertThat(payload.get("blockedId")).isEqualTo(targetId.toString());
        assertThat(payload.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("unblockUser should delete block and publish USER_UNBLOCKED to user.events")
    @SuppressWarnings("unchecked")
    void unblockUser_ShouldPublishKafkaEvent() {
        UUID blockerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        userService.unblockUser(blockerId, targetId);

        verify(blockRepository).deleteByBlockerIdAndBlockedId(blockerId, targetId);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("user.events"), eq(blockerId.toString()), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("USER_UNBLOCKED");
        assertThat(payload.get("blockerId")).isEqualTo(blockerId.toString());
        assertThat(payload.get("blockedId")).isEqualTo(targetId.toString());
        assertThat(payload.get("timestamp")).isNotNull();
    }
}
