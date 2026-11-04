package com.example.travelplatform.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 应用级基础设施配置：密码哈希、CORS 白名单与 JSON 严格反序列化。
 * 仅使用 spring-security-crypto 的 BCrypt，不引入 Spring Security 过滤器链。
 */
@Configuration
public class AppConfig {

    /** BCrypt 强度，按 OWASP 建议不低于 10。 */
    private static final int BCRYPT_STRENGTH = 10;

    /** 上传与前端可携带的请求头，具体白名单来自环境占位。 */
    private static final String[] ALLOWED_HEADERS = {"satoken", "Content-Type", "Accept"};

    private static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * 收紧 JSON 反序列化：拒绝数值/布尔等标量到文本的隐式转换，
     * 与 application.yml 中的 fail-on-unknown-properties 一起构成请求体契约。
     * 不在此处全局把 Long 序列化为字符串（ID 规则由各 VO/DTO 自行声明）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictJsonCoercionCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> objectMapper
                .coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }

    @Bean
    public WebMvcConfigurer corsConfigurer(
            @Value("${travel.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/v1/**")
                        .allowedOrigins(parseOrigins(allowedOrigins))
                        .allowedMethods(ALLOWED_METHODS)
                        .allowedHeaders(ALLOWED_HEADERS)
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }

    private static String[] parseOrigins(String rawOrigins) {
        return Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
