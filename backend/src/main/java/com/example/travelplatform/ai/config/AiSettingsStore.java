package com.example.travelplatform.ai.config;

import com.example.travelplatform.ai.dto.AiSettingsRequest;
import com.example.travelplatform.ai.vo.AiSettingsView;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

/** A saved immutable configuration overrides startup defaults for subsequent requests. */
@Component
public class AiSettingsStore {
    private static final Set<String> PROVIDERS = Set.of("custom", "openai", "deepseek", "qwen");
    private static final Set<String> FORMATS = Set.of("auto", "json_schema", "json_object");
    private final AiProperties defaults;
    private final Path file;
    private final ObjectMapper json;
    private volatile Stored current;
    private String warning;

    private record Stored(long version, AiProperties properties) {
        @Override
        public String toString() {
            return "StoredAiSettings[redacted]";
        }
    }

    public AiSettingsStore(AiProperties defaults, @Value("${travel.ai.settings-file}") String filename,
                           ObjectMapper json) {
        this.defaults = defaults;
        this.file = Path.of(filename).toAbsolutePath().normalize();
        this.json = json;
        load();
    }

    public AiProperties current() {
        return current.properties();
    }

    public synchronized AiSettingsView view() {
        AiProperties value = current.properties();
        var resolved = value.resolved();
        List<AiSettingsView.Preset> presets = List.of("openai", "deepseek", "qwen", "custom").stream()
                .map(id -> {
                    var preset = new AiProperties(false, id, "", "", "", 5, 45, 6000, "auto").resolved();
                    return new AiSettingsView.Preset(preset.provider(), preset.label(), preset.baseUrl(),
                            preset.model(), preset.responseFormat());
                }).toList();
        return new AiSettingsView(current.version(), value.enabled(), resolved.provider(), resolved.baseUrl(),
                resolved.model(), blank(value.responseFormat()) ? "auto" : value.responseFormat(),
                value.connectTimeoutSeconds(), value.timeoutSeconds(), value.maxOutputTokens(),
                !blank(value.apiKey()), current.version() == 0 ? "environment" : "saved", warning, presets);
    }

    public synchronized AiSettingsView save(AiSettingsRequest request) {
        if (request == null || request.version() == null || request.enabled() == null) {
            throw invalid("请提供完整的AI设置");
        }
        if (request.version() != current.version()) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "AI设置已被更新，请重新加载后保存");
        }
        String provider = text(request.provider());
        String format = text(request.responseFormat());
        if (!PROVIDERS.contains(provider) || !FORMATS.contains(format)) {
            throw invalid("请选择有效的供应商和响应格式");
        }
        if (request.connectTimeoutSeconds() == null || request.timeoutSeconds() == null
                || request.maxOutputTokens() == null) {
            throw invalid("请填写超时与输出长度");
        }
        if (request.clearApiKey() && !blank(request.apiKey())) {
            throw invalid("清除密钥时请留空API Key");
        }
        AiProperties previous = current.properties();
        String key = blank(request.apiKey()) ? previous.apiKey() : request.apiKey().strip();
        if (request.clearApiKey()) key = "";
        AiProperties candidate = new AiProperties(request.enabled(), provider, text(request.baseUrl()), key,
                text(request.model()), request.connectTimeoutSeconds(), request.timeoutSeconds(),
                request.maxOutputTokens(), format);
        // An existing credential cannot silently follow an endpoint change.
        if (!previous.resolved().baseUrl().equals(candidate.resolved().baseUrl()) && blank(request.apiKey())) {
            candidate = new AiProperties(candidate.enabled(), candidate.provider(), candidate.baseUrl(), "",
                    candidate.model(), candidate.connectTimeoutSeconds(), candidate.timeoutSeconds(),
                    candidate.maxOutputTokens(), candidate.responseFormat());
        }
        validate(candidate);
        if (current.version() == Long.MAX_VALUE) throw new ApiException(ErrorCode.STATE_CONFLICT);
        Stored next = new Stored(current.version() + 1, candidate);
        persist(next);
        current = next;
        warning = null;
        return view();
    }

    private void load() {
        current = new Stored(0, defaults);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file) || Files.size(file) > 65536) {
                throw new IOException("Invalid settings file");
            }
            Stored saved = json.readValue(Files.readAllBytes(file), Stored.class);
            if (saved.version() < 1) throw new IOException("Invalid settings version");
            validate(saved.properties());
            current = saved;
        } catch (IOException | RuntimeException error) {
            current = new Stored(0, new AiProperties(false, defaults.provider(), defaults.baseUrl(), defaults.apiKey(),
                    defaults.model(), defaults.connectTimeoutSeconds(), defaults.timeoutSeconds(),
                    defaults.maxOutputTokens(), defaults.responseFormat()));
            warning = "已保存的AI设置读取失败，请重新保存设置";
        }
    }

    private void persist(Stored value) {
        Path temporary = null;
        try {
            if (Files.isSymbolicLink(file)) throw new IOException("Settings file is a symbolic link");
            Files.createDirectories(file.getParent(), permissions("rwx------"));
            temporary = Files.createTempFile(file.getParent(), ".ai-settings-", ".tmp", permissions("rw-------"));
            Files.write(temporary, json.writeValueAsBytes(value), StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException error) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI设置保存失败，请稍后重试");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) { }
            }
        }
    }

    private static FileAttribute<?>[] permissions(String value) {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return new FileAttribute<?>[0];
        }
        return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(value))};
    }

    private static void validate(AiProperties value) {
        if (value == null || !PROVIDERS.contains(value.provider()) || !FORMATS.contains(value.responseFormat())) {
            throw invalid("AI设置格式无效");
        }
        if (value.connectTimeoutSeconds() < 1 || value.connectTimeoutSeconds() > 30
                || value.timeoutSeconds() < 1 || value.timeoutSeconds() > 90
                || value.maxOutputTokens() < 128 || value.maxOutputTokens() > 12000) {
            throw invalid("连接超时须为1–30秒，请求超时为1–90秒，输出上限为128–12000");
        }
        if (value.apiKey() != null && (value.apiKey().length() > 8192
                || value.apiKey().chars().anyMatch(c -> c < 32 || c == 127))) {
            throw invalid("API Key格式无效");
        }
        var resolved = value.resolved();
        if (resolved.model().length() > 120 || resolved.model().chars().anyMatch(c -> c < 32 || c == 127)) {
            throw invalid("模型名称格式无效");
        }
        if (!blank(resolved.baseUrl())) {
            try {
                URI url = URI.create(resolved.baseUrl());
                if (resolved.baseUrl().length() > 2000 || url.getHost() == null
                        || !("https".equals(url.getScheme()) || "http".equals(url.getScheme()))
                        || url.getUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null) {
                    throw invalid("请填写有效的HTTP或HTTPS API地址");
                }
            } catch (IllegalArgumentException error) {
                throw invalid("请填写有效的HTTP或HTTPS API地址");
            }
        }
        if (value.enabled() && !value.available()) {
            throw invalid("启用AI前请填写API地址、模型和API Key");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
