package com.example.travelplatform.ai.controller;

import com.example.travelplatform.ai.config.AiSettingsStore;
import com.example.travelplatform.ai.dto.AiSettingsRequest;
import com.example.travelplatform.ai.vo.AiSettingsView;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ai-settings")
public class AdminAiSettingsController {
    private final AiSettingsStore settings;
    private final UserService users;

    public AdminAiSettingsController(AiSettingsStore settings, UserService users) {
        this.settings = settings;
        this.users = users;
    }

    @GetMapping
    @Operation(summary="管理员读取AI设置，密钥仅返回是否已配置")
    public ApiResponse<AiSettingsView> read() {
        users.requireAdministrator();
        return ApiResponse.success(settings.view());
    }

    @PutMapping
    @Operation(summary="管理员保存AI设置，后续请求即时生效")
    public ApiResponse<AiSettingsView> save(@RequestBody AiSettingsRequest request) {
        users.requireAdministrator();
        return ApiResponse.success(settings.save(request));
    }
}
