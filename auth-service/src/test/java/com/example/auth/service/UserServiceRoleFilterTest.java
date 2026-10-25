package com.example.auth.service;

import com.example.auth.dto.UserProfileResponse;
import com.example.auth.dto.UserSummaryDto;
import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import com.example.auth.exception.UserNotFoundException;
import com.example.auth.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceRoleFilterTest {

    @Mock private AuthUserRepository userRepository;
    @Mock private AuthUserRoleRepository userRoleRepository;
    @Mock private AuthBlockRepository blockRepository;
    @Mock private AuthNoteRepository noteRepository;
    @Mock private AuthRefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserService userService;

    private AuthUser createTestUser(UUID id, String username, Role role, boolean banned) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName("Display " + username);
        user.setRole(role);
        user.setBanned(banned);
        user.setCreatedAt(Instant.now());
        return user;
    }

    @Test
    @DisplayName("searchUsers with blank query should delegate to findActiveStandardUsersOrderByCreatedAtDesc with Role.USER")
    void searchUsers_BlankQuery_ShouldFilterRoleUser() {
        Pageable pageable = PageRequest.of(0, 10);
        AuthUser user1 = createTestUser(UUID.randomUUID(), "john_doe", Role.USER, false);
        Page<AuthUser> userPage = new PageImpl<>(List.of(user1));

        when(userRepository.findActiveStandardUsersOrderByCreatedAtDesc(Role.USER, pageable)).thenReturn(userPage);

        Page<UserProfileResponse> result = userService.searchUsers("", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("john_doe");
        assertThat(result.getContent().get(0).getRole()).isEqualTo("USER");
        verify(userRepository).findActiveStandardUsersOrderByCreatedAtDesc(Role.USER, pageable);
    }

    @Test
    @DisplayName("searchUsers with query should delegate to searchActiveStandardUsers with Role.USER")
    void searchUsers_WithQuery_ShouldFilterRoleUser() {
        Pageable pageable = PageRequest.of(0, 10);
        AuthUser user1 = createTestUser(UUID.randomUUID(), "alice", Role.USER, false);
        Page<AuthUser> userPage = new PageImpl<>(List.of(user1));

        when(userRepository.searchActiveStandardUsers("alice", Role.USER, pageable)).thenReturn(userPage);

        Page<UserProfileResponse> result = userService.searchUsers("alice", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("alice");
        verify(userRepository).searchActiveStandardUsers("alice", Role.USER, pageable);
    }

    @Test
    @DisplayName("getUsersSummaryBatch should filter out non-USER roles and users with dynamic non-standard roles")
    void getUsersSummaryBatch_ShouldFilterOutAdminAndNonStandardRoles() {
        UUID u1 = UUID.randomUUID(); // Valid USER
        UUID u2 = UUID.randomUUID(); // ADMIN enum
        UUID u3 = UUID.randomUUID(); // USER enum but has MODERATOR in user_roles

        AuthUser user1 = createTestUser(u1, "regular_user", Role.USER, false);
        AuthUser user2 = createTestUser(u2, "admin_user", Role.ADMIN, false);
        AuthUser user3 = createTestUser(u3, "mod_user", Role.USER, false);

        when(userRepository.findAllById(anySet())).thenReturn(List.of(user1, user2, user3));
        when(userRoleRepository.findNonStandardRoleUserIds()).thenReturn(List.of(u3));

        List<UserSummaryDto> summaries = userService.getUsersSummaryBatch(List.of(u1, u2, u3));

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getUsername()).isEqualTo("regular_user");
        assertThat(summaries.get(0).getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("getPublicProfile for ADMIN target viewed by another user should throw UserNotFoundException")
    void getPublicProfile_AdminTarget_ViewedByOther_ShouldThrowNotFound() {
        UUID viewerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AuthUser adminUser = createTestUser(adminId, "admin", Role.ADMIN, false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> userService.getPublicProfile(viewerId, "admin"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("getPublicProfile for USER target with MODERATOR dynamic role viewed by another user should throw UserNotFoundException")
    void getPublicProfile_ModeratorTarget_ViewedByOther_ShouldThrowNotFound() {
        UUID viewerId = UUID.randomUUID();
        UUID modId = UUID.randomUUID();
        AuthUser modUser = createTestUser(modId, "mod_user", Role.USER, false);

        when(userRepository.findByUsername("mod_user")).thenReturn(Optional.of(modUser));
        when(userRoleRepository.hasNonStandardRole(modId)).thenReturn(true);

        assertThatThrownBy(() -> userService.getPublicProfile(viewerId, "mod_user"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("getPublicProfile for regular USER viewed by another user should succeed")
    void getPublicProfile_RegularUser_ViewedByOther_ShouldSucceed() {
        UUID viewerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        AuthUser target = createTestUser(targetId, "normal_user", Role.USER, false);

        when(userRepository.findByUsername("normal_user")).thenReturn(Optional.of(target));
        when(userRoleRepository.hasNonStandardRole(targetId)).thenReturn(false);
        when(blockRepository.existsByBlockerIdAndBlockedIdOrBlockerIdAndBlockedId(viewerId, targetId, targetId, viewerId)).thenReturn(false);

        UserProfileResponse response = userService.getPublicProfile(viewerId, "normal_user");

        assertThat(response.getUsername()).isEqualTo("normal_user");
        assertThat(response.getRole()).isEqualTo("USER");
    }
}
