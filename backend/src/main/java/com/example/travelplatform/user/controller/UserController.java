package com.example.travelplatform.user.controller;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.user.dto.ProfilePatchRequest;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.CurrentUserVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @GetMapping
    @Operation(summary = "读取我的资料")
    public ApiResponse<CurrentUserVO> current() { return ApiResponse.success(users.current()); }

    @PatchMapping
    @Operation(summary = "部分修改我的资料")
    public ApiResponse<CurrentUserVO> update(@RequestBody ProfilePatchRequest request) {
        return ApiResponse.success(users.updateProfile(request));
    }
}
