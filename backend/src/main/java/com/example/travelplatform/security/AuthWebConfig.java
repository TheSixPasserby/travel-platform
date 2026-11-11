package com.example.travelplatform.security;

import com.example.travelplatform.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final UserService users;

    public AuthWebConfig(UserService users) { this.users = users; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String path = request.getServletPath();
                boolean publicDestination = "GET".equals(request.getMethod())
                        && (path.equals("/api/v1/destinations") || path.matches("/api/v1/destinations/[^/]+")
                        || path.matches("/api/v1/destinations/[^/]+/guides"));
                boolean publicGuide = "GET".equals(request.getMethod()) && (path.equals("/api/v1/guides")
                        || path.matches("/api/v1/guides/[^/]+") || path.matches("/api/v1/guides/[^/]+/comments") || path.equals("/api/v1/tags")
                        || path.matches("/api/v1/users/[^/]+/profile")
                        || path.matches("/api/v1/users/by-username/[^/]+(?:/guides(?:/feed)?)?")
                        || path.matches("/api/v1/guides/[^/]+/related"));
                if (!"OPTIONS".equals(request.getMethod()) && !publicDestination && !publicGuide) users.requireCurrentUser();
                return true;
            }
        }).addPathPatterns("/api/v1/**").excludePathPatterns(
                "/api/v1/auth/register", "/api/v1/auth/login",
                "/api/v1/openapi", "/api/v1/openapi/**", "/api/v1/swagger-ui.html",
                "/api/v1/swagger-ui/**");
    }
}
