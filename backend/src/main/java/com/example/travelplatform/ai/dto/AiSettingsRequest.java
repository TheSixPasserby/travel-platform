package com.example.travelplatform.ai.dto;

public record AiSettingsRequest(Long version, Boolean enabled, String provider, String baseUrl,
        String model, String apiKey, boolean clearApiKey, String responseFormat,
        Integer connectTimeoutSeconds, Integer timeoutSeconds, Integer maxOutputTokens) {
    @Override public String toString() { return "AiSettingsRequest[redacted]"; }
}
