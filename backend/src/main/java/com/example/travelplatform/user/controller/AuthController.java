package com.example.travelplatform.user.controller;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.user.dto.LoginRequest;
import com.example.travelplatform.user.dto.RegisterRequest;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.CurrentUserVO;
import com.example.travelplatform.user.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService users;

    public AuthController(UserService users) { this.users = users; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "注册账号，不自动登录")
    @SecurityRequirements
    public ApiResponse<CurrentUserVO> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(users.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "登录并创建独立会话")
    @SecurityRequirements
    public ApiResponse<LoginVO> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(users.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "读取当前用户")
    public ApiResponse<CurrentUserVO> current() { return ApiResponse.success(users.current()); }

    @PostMapping("/logout")
    @Operation(summary = "退出当前会话")
    public ApiResponse<Void> logout() {
        users.logout();
        return ApiResponse.success(null);
    }
}
