package com.example.auth.service;

import com.example.auth.dto.AdminUserResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.repository.AuthUserRoleRepository;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceUsersFilterTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Mock
    private RoleService roleService;

    @Mock
    private AuthUserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    private AuthUser createSampleUser(String username, String email, Role role, boolean banned, boolean emailVerified) {
        AuthUser user = new AuthUser();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role);
        user.setBanned(banned);
        user.setEmailVerified(emailVerified);
        user.setCreatedAt(Instant.now());
        return user;
    }

    @Test
    @DisplayName("getUsers with role filter should query userRoleRepository and call findAll with specification")
    @SuppressWarnings("unchecked")
    void getUsers_WithRoleFilter_ShouldQueryRoleAndFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID dynamicUserUuid = UUID.randomUUID();
        when(userRoleRepository.findUserIdsByRole("MODERATOR")).thenReturn(List.of(dynamicUserUuid));

        AuthUser user = createSampleUser("mod_user", "mod@example.com", Role.USER, false, true);
        Page<AuthUser> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(roleService.getUserRoles(user.getId())).thenReturn(List.of("USER", "MODERATOR"));

        Page<AdminUserResponse> result = adminService.getUsers("mod", "ACTIVE", "MODERATOR", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("mod_user");
        verify(userRoleRepository).findUserIdsByRole("MODERATOR");
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("getUsers without role filter should not query userRoleRepository")
    @SuppressWarnings("unchecked")
    void getUsers_WithoutRoleFilter_ShouldNotQueryUserRoleRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        AuthUser user = createSampleUser("active_user", "active@example.com", Role.USER, false, true);
        Page<AuthUser> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(roleService.getUserRoles(user.getId())).thenReturn(List.of("USER"));

        Page<AdminUserResponse> result = adminService.getUsers("active", "ACTIVE", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verifyNoInteractions(userRoleRepository);
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("getUsers overloaded convenience method should delegate with null filters")
    @SuppressWarnings("unchecked")
    void getUsers_Overloaded_ShouldDelegateWithNullFilters() {
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        Page<AdminUserResponse> result = adminService.getUsers(pageable);

        assertThat(result.getContent()).isEmpty();
        verifyNoInteractions(userRoleRepository);
        verify(userRepository).findAll(any(Specification.class), eq(pageable));
    }
}
