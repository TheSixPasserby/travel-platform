package com.example.travelplatform.ai.vo;

import java.util.List;

public record AiSettingsView(long version, boolean enabled, String provider, String baseUrl,
        String model, String responseFormat, int connectTimeoutSeconds, int timeoutSeconds,
        int maxOutputTokens, boolean apiKeyConfigured, String source, String warning,
        List<Preset> presets) {
    public record Preset(String id, String label, String baseUrl, String model, String responseFormat) { }
}
