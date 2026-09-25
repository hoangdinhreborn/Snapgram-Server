package com.example.media.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Snapgram — Media Service API")
                        .version("0.1.0")
                        .description("""
                                Service quản lý upload, xử lý và lưu trữ media (ảnh, video) trên MinIO.
                                
                                **Xác thực:**
                                - Khi đi qua API Gateway, Gateway sẽ tự động trích xuất JWT và inject header `X-User-Id`.
                                - Khi test trực tiếp trên Swagger UI, hãy click **Authorize** và nhập UUID user (ví dụ: `11111111-1111-1111-1111-111111111111`).
                                """))
                .servers(List.of(
                        new Server().url("http://localhost:8085").description("Direct Media Service"),
                        new Server().url("http://localhost:8080").description("API Gateway")
                ))
                .addSecurityItem(new SecurityRequirement().addList(USER_ID_HEADER))
                .components(new Components()
                        .addSecuritySchemes(USER_ID_HEADER, new SecurityScheme()
                                .name(USER_ID_HEADER)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Nhập User UUID (được inject từ API Gateway qua header X-User-Id)")));
    }
}
