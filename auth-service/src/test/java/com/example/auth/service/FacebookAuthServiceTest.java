package com.example.auth.service;

import com.example.auth.client.FacebookClient;
import com.example.auth.dto.auth.AuthResponse;
import com.example.auth.dto.auth.FacebookLoginRequest;
import com.example.auth.dto.auth.FacebookUserProfile;
import com.example.auth.entity.AuthOAuthAccount;
import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import com.example.auth.exception.AccountBannedException;
import com.example.auth.exception.OAuthAuthenticationException;
import com.example.auth.repository.AuthOAuthAccountRepository;
import com.example.auth.repository.AuthRefreshTokenRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacebookAuthServiceTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private AuthRefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthOAuthAccountRepository oAuthAccountRepository;

    @Mock
    private FacebookClient facebookClient;

    @Mock
    private JwtService jwtService;

    @Mock
    private RoleService roleService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TwoFaService twoFaService;

    @InjectMocks
    private AuthService authService;

    private FacebookUserProfile fbProfile;

    @BeforeEach
    void setUp() {
        fbProfile = FacebookUserProfile.builder()
                .id("fb_123456789")
                .name("Nguyễn Văn A")
                .email("nguyenvana@gmail.com")
                .picture(FacebookUserProfile.PictureWrapper.builder()
                        .data(FacebookUserProfile.PictureData.builder()
                                .url("https://graph.facebook.com/picture.jpg")
                                .build())
                        .build())
                .build();
    }

    private void mockTokenGeneration(AuthUser user) {
        when(roleService.getUserRoles(user.getId())).thenReturn(Collections.singletonList("USER"));
        when(roleService.getUserPermissions(user.getId())).thenReturn(Collections.emptyList());
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("mock_fb_access_token");
        when(jwtService.getExpiration("mock_fb_access_token")).thenReturn(Instant.now().plus(Duration.ofMinutes(15)));
        when(jwtService.generateRefreshToken(any())).thenReturn("mock_fb_refresh_token");
        when(jwtService.getJti("mock_fb_refresh_token")).thenReturn("mock_fb_jti");
        when(jwtService.getExpiration("mock_fb_refresh_token")).thenReturn(Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("loginWithFacebook with code should exchange code and create new user and oauth link")
    void loginWithFacebook_NewUser_ShouldCreateUserAndOAuthAccount() {
        FacebookLoginRequest request = FacebookLoginRequest.builder()
                .code("fb_auth_code_123")
                .redirectUri("http://localhost:3000/oauth/callback/facebook")
                .build();

        when(facebookClient.exchangeCodeForAccessToken("fb_auth_code_123", "http://localhost:3000/oauth/callback/facebook"))
                .thenReturn("mock_fb_token");
        when(facebookClient.getUserProfile("mock_fb_token")).thenReturn(fbProfile);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("FACEBOOK", "fb_123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_random_pwd");

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser u = invocation.getArgument(0);
            u.setId(generatedId);
            return u;
        });

        AuthUser userForTokens = new AuthUser();
        userForTokens.setId(generatedId);
        userForTokens.setUsername("nguyen_van_a");
        userForTokens.setEmail("nguyenvana@gmail.com");
        userForTokens.setRole(Role.USER);
        mockTokenGeneration(userForTokens);

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_fb_access_token");
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("nguyenvana@gmail.com");

        verify(userRepository, atLeastOnce()).save(any(AuthUser.class));
        verify(roleService).assignRole(generatedId, "USER");
        verify(oAuthAccountRepository).save(any(AuthOAuthAccount.class));
    }

    @Test
    @DisplayName("loginWithFacebook with existing oauth account should return tokens directly without creating user")
    void loginWithFacebook_ExistingOAuth_ShouldReturnTokensDirectly() {
        FacebookLoginRequest request = FacebookLoginRequest.builder()
                .accessToken("direct_token_xyz")
                .build();

        UUID existingUserId = UUID.randomUUID();
        AuthUser existingUser = new AuthUser();
        existingUser.setId(existingUserId);
        existingUser.setUsername("existing_fb_user");
        existingUser.setEmail("nguyenvana@gmail.com");
        existingUser.setAvatarUrl("https://existing.com/pic.jpg");
        existingUser.setRole(Role.USER);

        AuthOAuthAccount oAuthAccount = AuthOAuthAccount.builder()
                .userId(existingUserId)
                .provider("FACEBOOK")
                .providerUserId("fb_123456789")
                .build();

        when(facebookClient.getUserProfile("direct_token_xyz")).thenReturn(fbProfile);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("FACEBOOK", "fb_123456789"))
                .thenReturn(Optional.of(oAuthAccount));
        when(userRepository.findById(existingUserId)).thenReturn(Optional.of(existingUser));

        mockTokenGeneration(existingUser);

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_fb_access_token");
        assertThat(response.getUser().getUsername()).isEqualTo("existing_fb_user");

        verify(userRepository, never()).save(any(AuthUser.class));
        verify(roleService, never()).assignRole(any(), any());
    }

    @Test
    @DisplayName("loginWithFacebook for existing user with same email should link oauth account")
    void loginWithFacebook_ExistingEmail_ShouldLinkAccount() {
        FacebookLoginRequest request = FacebookLoginRequest.builder()
                .accessToken("token_for_link")
                .build();

        UUID existingUserId = UUID.randomUUID();
        AuthUser existingUser = new AuthUser();
        existingUser.setId(existingUserId);
        existingUser.setUsername("existing_snapgram_user");
        existingUser.setEmail("nguyenvana@gmail.com");
        existingUser.setEmailVerified(false);
        existingUser.setRole(Role.USER);

        when(facebookClient.getUserProfile("token_for_link")).thenReturn(fbProfile);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("FACEBOOK", "fb_123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("nguyenvana@gmail.com")).thenReturn(Optional.of(existingUser));

        mockTokenGeneration(existingUser);

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response).isNotNull();
        assertThat(existingUser.isEmailVerified()).isTrue();
        verify(oAuthAccountRepository).save(any(AuthOAuthAccount.class));
    }

    @Test
    @DisplayName("loginWithFacebook for banned user should throw AccountBannedException")
    void loginWithFacebook_BannedUser_ShouldThrowAccountBannedException() {
        FacebookLoginRequest request = FacebookLoginRequest.builder()
                .accessToken("token_banned")
                .build();

        UUID bannedUserId = UUID.randomUUID();
        AuthUser bannedUser = new AuthUser();
        bannedUser.setId(bannedUserId);
        bannedUser.setUsername("bad_actor");
        bannedUser.setEmail("nguyenvana@gmail.com");
        bannedUser.setBanned(true);
        bannedUser.setBanReason("Spam behavior");

        AuthOAuthAccount oAuthAccount = AuthOAuthAccount.builder()
                .userId(bannedUserId)
                .provider("FACEBOOK")
                .providerUserId("fb_123456789")
                .build();

        when(facebookClient.getUserProfile("token_banned")).thenReturn(fbProfile);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("FACEBOOK", "fb_123456789"))
                .thenReturn(Optional.of(oAuthAccount));
        when(userRepository.findById(bannedUserId)).thenReturn(Optional.of(bannedUser));

        assertThatThrownBy(() -> authService.loginWithFacebook(request))
                .isInstanceOf(AccountBannedException.class)
                .hasMessageContaining("Spam behavior");
    }

    @Test
    @DisplayName("loginWithFacebook for user with 2FA enabled should return tempToken and requiresTwoFa=true")
    void loginWithFacebook_TwoFaEnabled_ShouldRequireTwoFa() {
        FacebookLoginRequest request = FacebookLoginRequest.builder()
                .accessToken("token_2fa")
                .build();

        UUID twoFaUserId = UUID.randomUUID();
        AuthUser twoFaUser = new AuthUser();
        twoFaUser.setId(twoFaUserId);
        twoFaUser.setUsername("secure_user");
        twoFaUser.setEmail("nguyenvana@gmail.com");
        twoFaUser.setTwoFaEnabled(true);

        AuthOAuthAccount oAuthAccount = AuthOAuthAccount.builder()
                .userId(twoFaUserId)
                .provider("FACEBOOK")
                .providerUserId("fb_123456789")
                .build();

        when(facebookClient.getUserProfile("token_2fa")).thenReturn(fbProfile);
        when(oAuthAccountRepository.findByProviderAndProviderUserId("FACEBOOK", "fb_123456789"))
                .thenReturn(Optional.of(oAuthAccount));
        when(userRepository.findById(twoFaUserId)).thenReturn(Optional.of(twoFaUser));
        when(jwtService.generateTempToken(twoFaUser)).thenReturn("temp_2fa_token_123");

        AuthResponse response = authService.loginWithFacebook(request);

        assertThat(response).isNotNull();
        assertThat(response.getRequiresTwoFa()).isTrue();
        assertThat(response.getTempToken()).isEqualTo("temp_2fa_token_123");
        assertThat(response.getAccessToken()).isNull();
    }

    @Test
    @DisplayName("loginWithFacebook with missing code and token should throw OAuthAuthenticationException")
    void loginWithFacebook_MissingCredentials_ShouldThrowOAuthAuthenticationException() {
        FacebookLoginRequest request = FacebookLoginRequest.builder().build();

        assertThatThrownBy(() -> authService.loginWithFacebook(request))
                .isInstanceOf(OAuthAuthenticationException.class)
                .hasMessageContaining("Mã xác thực (code) hoặc accessToken Facebook là bắt buộc");
    }
}
