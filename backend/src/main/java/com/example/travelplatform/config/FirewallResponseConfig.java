package com.example.travelplatform.config;

import cn.dev33.satoken.strategy.SaFirewallStrategy;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Preserve Sa-Token's firewall checks and normalize its pre-MVC rejection response. */
@Configuration
public class FirewallResponseConfig implements InitializingBean {
    private final ObjectMapper json;
    public FirewallResponseConfig(ObjectMapper json) { this.json = json; }

    @Override public void afterPropertiesSet() {
        SaFirewallStrategy.instance.checkFailHandle = (exception, request, response, handler) -> {
            HttpServletResponse servlet = (HttpServletResponse) response.getSource();
            servlet.setStatus(400);
            servlet.setContentType("application/json");
            servlet.setCharacterEncoding(StandardCharsets.UTF_8.name());
            try {
                json.writeValue(servlet.getWriter(), ApiResponse.failure(
                        ErrorCode.VALIDATION_FAILED.getCode(), "请求路径或格式不合法", null));
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot send error response", failure);
            }
        };
    }
}
