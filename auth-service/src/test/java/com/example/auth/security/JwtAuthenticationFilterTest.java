package com.example.auth.security;

import com.example.auth.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private CookieUtils cookieUtils;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("shouldNotFilter should return true for /api/auth/verify")
    void shouldNotFilter_VerifyEndpoint_ShouldReturnTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/api/auth/verify");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    @DisplayName("Filter should resolve admin_access_token on shared endpoint like /api/auth/me when access_token is absent")
    void doFilter_AdminCookieOnSharedEndpoint_ShouldAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String token = "admin-jwt-token";
        UUID adminId = UUID.randomUUID();

        when(cookieUtils.extractCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE)).thenReturn(Optional.empty());
        when(cookieUtils.extractCookieValue(request, CookieUtils.ADMIN_ACCESS_TOKEN_COOKIE)).thenReturn(Optional.of(token));

        when(jwtService.isAccessToken(token)).thenReturn(true);
        when(jwtService.getJti(token)).thenReturn("jti-123");
        when(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(false);
        when(jwtService.getUserId(token)).thenReturn(adminId);
        when(jwtService.getRoles(token)).thenReturn(List.of("ROLE_ADMIN"));
        when(jwtService.getPermissions(token)).thenReturn(List.of("READ", "WRITE"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(adminId);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ADMIN", "READ", "WRITE");
    }

    @Test
    @DisplayName("Filter should resolve access_token as primary cookie on shared endpoint /api/auth/me")
    void doFilter_UserCookieOnSharedEndpoint_ShouldAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String token = "user-jwt-token";
        UUID userId = UUID.randomUUID();

        when(cookieUtils.extractCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE)).thenReturn(Optional.of(token));

        when(jwtService.isAccessToken(token)).thenReturn(true);
        when(jwtService.getJti(token)).thenReturn("jti-456");
        when(tokenBlacklistService.isBlacklisted("jti-456")).thenReturn(false);
        when(jwtService.getUserId(token)).thenReturn(userId);
        when(jwtService.getRoles(token)).thenReturn(List.of("ROLE_USER"));
        when(jwtService.getPermissions(token)).thenReturn(List.of());

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
    }
}
