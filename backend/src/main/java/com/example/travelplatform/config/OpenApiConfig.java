package com.example.travelplatform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc 基础配置：接口文档路径由 application.yml 指定为 /api/v1/openapi，
 * UI 为 /api/v1/swagger-ui.html；登录令牌以 satoken 请求头声明，文档中不填真实凭据。
 */
@Configuration
public class OpenApiConfig {

    private static final String TOKEN_SCHEME_NAME = "satoken";

    @Bean
    public OpenAPI travelPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Travel Platform API")
                        .version("v1")
                        .description("旅游攻略分享与行程规划平台后端接口，统一前缀 /api/v1。"))
                .components(new Components()
                        .addSecuritySchemes(TOKEN_SCHEME_NAME, new SecurityScheme()
                                .name(TOKEN_SCHEME_NAME)
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("登录成功后服务端签发的登录令牌；请勿在文档中填写真实凭据。")))
                .addSecurityItem(new SecurityRequirement().addList(TOKEN_SCHEME_NAME));
    }
}
