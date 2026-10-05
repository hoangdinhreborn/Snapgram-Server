package com.example.recommender.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public OpenAPI recommenderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Recommender & Feed Service API")
                        .description("CQRS Read-model và bộ máy đề xuất, xếp hạng feed và gợi ý người dùng")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(USER_ID_HEADER))
                .components(new Components()
                        .addSecuritySchemes(USER_ID_HEADER, new SecurityScheme()
                                .name(USER_ID_HEADER)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Nhập User UUID (được inject từ API Gateway qua header X-User-Id)")));
    }
}
