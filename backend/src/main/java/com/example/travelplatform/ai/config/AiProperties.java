package com.example.travelplatform.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.Locale;

@ConfigurationProperties("travel.ai")
public record AiProperties(boolean enabled, String provider, String baseUrl, String apiKey, String model,
                           int connectTimeoutSeconds, int timeoutSeconds, int maxOutputTokens, String responseFormat) {
    public record Resolved(String provider, String label, String baseUrl, String model, String responseFormat) { }

    public Resolved resolved() {
        String id = blank(provider) ? "custom" : provider.trim().toLowerCase(Locale.ROOT);
        String label;
        String defaultUrl;
        String defaultModel;
        String defaultFormat;
        switch (id) {
            case "openai" -> {
                label = "OpenAI";
                defaultUrl = "https://api.openai.com/v1";
                defaultModel = "gpt-4.1-mini";
                defaultFormat = "json_schema";
            }
            case "deepseek" -> {
                label = "DeepSeek";
                defaultUrl = "https://api.deepseek.com";
                defaultModel = "deepseek-flash";
                defaultFormat = "json_object";
            }
            case "qwen" -> {
                label = "通义千问";
                defaultUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
                defaultModel = "qwen-plus";
                defaultFormat = "json_object";
            }
            default -> {
                id = "custom";
                label = "自定义兼容服务";
                defaultUrl = "";
                defaultModel = "";
                defaultFormat = "json_object";
            }
        }
        String format = blank(responseFormat) || "auto".equals(responseFormat) ? defaultFormat : responseFormat;
        String url = blank(baseUrl) ? defaultUrl : baseUrl.strip();
        while (url.endsWith("/")) url = url.substring(0,url.length()-1);
        return new Resolved(id,label,url,blank(model) ? defaultModel : model.strip(),format);
    }

    public boolean available() {
        if (!enabled || blank(apiKey) || apiKey.length() > 8192 || apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0
                || connectTimeoutSeconds < 1 || connectTimeoutSeconds > 30 || timeoutSeconds < 1 || timeoutSeconds > 90
                || maxOutputTokens < 128 || maxOutputTokens > 12000) return false;
        try {
            Resolved value = resolved();
            URI uri = URI.create(value.baseUrl());
            return uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && !blank(value.model()) && value.model().length() <= 120
                    && ("json_schema".equals(value.responseFormat()) || "json_object".equals(value.responseFormat()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    @Override public String toString() { return "AiProperties[redacted]"; }
}
