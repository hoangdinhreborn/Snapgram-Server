package com.example.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Snapgram — Auth Service API")
                        .version("0.1.0")
                        .description("""
                                Authentication & User management service for Snapgram.

                                **Auth flow:**
                                1. `POST /api/auth/register` or `POST /api/auth/login` → nhận `accessToken`
                                2. Click **Authorize** ở góc phải, nhập `<accessToken>`
                                3. Tất cả endpoint có 🔒 sẽ tự gửi `Authorization: Bearer <token>`

                                **2FA flow (nếu đã bật):**
                                1. `POST /api/auth/login` → `{ requiresTwoFa: true, tempToken }`
                                2. `POST /api/auth/login/2fa` với `tempToken` + `totpCode`
                                """)
                        .contact(new Contact().name("Snapgram Dev")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập accessToken nhận được từ /api/auth/login")));
    }
}
