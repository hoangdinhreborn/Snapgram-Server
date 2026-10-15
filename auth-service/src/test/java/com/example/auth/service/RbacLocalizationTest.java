package com.example.auth.service;

import com.example.auth.dto.PermissionResponse;
import com.example.auth.entity.AuthPermission;
import com.example.auth.repository.AuthPermissionRepository;
import com.example.auth.repository.AuthRoleRepository;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.repository.AuthUserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacLocalizationTest {

    @Mock
    private AuthRoleRepository roleRepository;
    @Mock
    private AuthPermissionRepository permissionRepository;
    @Mock
    private AuthUserRoleRepository userRoleRepository;
    @Mock
    private AuthUserRepository userRepository;

    private ResourceBundleMessageSource messageSource;
    private RbacService rbacService;

    private static final Pattern PERMISSION_NAME_PATTERN = Pattern.compile("^[A-Z_]+:[A-Z_]+$");

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        rbacService = new RbacService(
                roleRepository,
                permissionRepository,
                userRoleRepository,
                userRepository,
                messageSource
        );
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Permission descriptions should localize to English when locale is set to en")
    void getPermissions_WhenLocaleIsEnglish_ShouldReturnEnglishDescriptions() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        AuthPermission p1 = AuthPermission.builder()
                .id(UUID.randomUUID())
                .name("USER:BAN")
                .category("USER_MANAGEMENT")
                .description("Khóa tài khoản người dùng vi phạm")
                .build();

        AuthPermission p2 = AuthPermission.builder()
                .id(UUID.randomUUID())
                .name("CONTENT:MODERATE")
                .category("CONTENT_MODERATION")
                .description("Truy cập trung tâm kiểm duyệt nội dung")
                .build();

        when(permissionRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(p1, p2));

        Map<String, List<PermissionResponse>> result = rbacService.getAllPermissionsGrouped();

        assertThat(result).containsKey("USER_MANAGEMENT");
        assertThat(result.get("USER_MANAGEMENT").get(0).getDescription()).isEqualTo("Ban violating user account");

        assertThat(result).containsKey("CONTENT_MODERATION");
        assertThat(result.get("CONTENT_MODERATION").get(0).getDescription()).isEqualTo("Access content moderation center");
    }

    @Test
    @DisplayName("Permission descriptions should localize to Vietnamese when locale is set to vi")
    void getPermissions_WhenLocaleIsVietnamese_ShouldReturnVietnameseDescriptions() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        AuthPermission p1 = AuthPermission.builder()
                .id(UUID.randomUUID())
                .name("USER:BAN")
                .category("USER_MANAGEMENT")
                .description("Khóa tài khoản người dùng vi phạm")
                .build();

        when(permissionRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(p1));

        Map<String, List<PermissionResponse>> result = rbacService.getAllPermissionsGrouped();

        assertThat(result.get("USER_MANAGEMENT").get(0).getDescription()).isEqualTo("Khóa tài khoản người dùng vi phạm");
    }

    @Test
    @DisplayName("Unknown permission should fallback to entity description in DB")
    void getPermissions_WhenUnknownPermission_ShouldFallbackToEntityDescription() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        AuthPermission pCustom = AuthPermission.builder()
                .id(UUID.randomUUID())
                .name("CUSTOM:SPECIAL_ACTION")
                .category("CUSTOM")
                .description("Custom DB Description")
                .build();

        when(permissionRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of(pCustom));

        Map<String, List<PermissionResponse>> result = rbacService.getAllPermissionsGrouped();

        assertThat(result.get("CUSTOM").get(0).getDescription()).isEqualTo("Custom DB Description");
    }

    @Test
    @DisplayName("All 17 standard permissions should match pattern ^[A-Z_]+:[A-Z_]+$")
    void standardPermissions_ShouldMatchNamingPattern() {
        List<String> permissionNames = List.of(
                "USER:READ", "USER:BAN", "USER:UNBAN", "USER:ROLE_ASSIGN",
                "CONTENT:MODERATE", "POST:DELETE", "COMMENT:DELETE", "STORY:DELETE",
                "REPORT:VIEW", "REPORT:RESOLVE", "REPORT:DISMISS",
                "SUPPORT:TICKET_READ", "SUPPORT:TICKET_MANAGE", "SUPPORT:USER_ASSIST",
                "SYSTEM:SETTINGS_READ", "SYSTEM:SETTINGS_WRITE", "AUDIT:VIEW"
        );

        for (String permName : permissionNames) {
            assertThat(PERMISSION_NAME_PATTERN.matcher(permName).matches())
                    .as("Permission " + permName + " must match ^[A-Z_]+:[A-Z_]+$")
                    .isTrue();

            String enDesc = messageSource.getMessage("permission." + permName.replace(':', '.') + ".description", null, Locale.ENGLISH);
            assertThat(enDesc).isNotBlank();

            String viDesc = messageSource.getMessage("permission." + permName.replace(':', '.') + ".description", null, Locale.forLanguageTag("vi"));
            assertThat(viDesc).isNotBlank();
        }
    }
}
