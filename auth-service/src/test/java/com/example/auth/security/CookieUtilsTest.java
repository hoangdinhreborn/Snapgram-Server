package com.example.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    @Test
    @DisplayName("Omits Domain when cookieDomain is blank or null")
    void omitsDomainWhenBlank() {
        CookieUtils cookieUtils = new CookieUtils(false, "  ");

        ResponseCookie access = cookieUtils.createAccessTokenCookie("tok", Duration.ofMinutes(15));
        ResponseCookie cleared = cookieUtils.clearAccessTokenCookie();

        assertThat(access.getDomain()).isNull();
        assertThat(access.isSecure()).isFalse();
        assertThat(access.toString()).doesNotContain("Domain=");
        assertThat(cleared.getDomain()).isNull();
    }

    @Test
    @DisplayName("Applies trimmed Domain and Secure flags on create and clear cookies")
    void appliesDomainWhenConfigured() {
        CookieUtils cookieUtils = new CookieUtils(true, " .maintaindev.id.vn ");

        ResponseCookie access = cookieUtils.createAccessTokenCookie("tok", Duration.ofMinutes(15));
        ResponseCookie admin = cookieUtils.createAdminAccessTokenCookie("adm", Duration.ofMinutes(30));
        ResponseCookie refresh = cookieUtils.createRefreshTokenCookie("ref", Duration.ofDays(30));
        ResponseCookie cleared = cookieUtils.clearAccessTokenCookie();

        assertThat(access.getDomain()).isEqualTo(".maintaindev.id.vn");
        assertThat(access.isSecure()).isTrue();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSameSite()).isEqualTo("Lax");

        assertThat(admin.getDomain()).isEqualTo(".maintaindev.id.vn");
        assertThat(admin.getSameSite()).isEqualTo("Strict");

        assertThat(refresh.getDomain()).isEqualTo(".maintaindev.id.vn");
        assertThat(refresh.getPath()).isEqualTo("/api/auth");

        assertThat(cleared.getDomain()).isEqualTo(".maintaindev.id.vn");
        assertThat(cleared.getMaxAge()).isZero();
    }
}
