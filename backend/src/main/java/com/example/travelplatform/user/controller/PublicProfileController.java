package com.example.travelplatform.user.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.PublicProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users") @SecurityRequirements
public class PublicProfileController {
    private final UserService users;
    public PublicProfileController(UserService users) { this.users=users; }
    @GetMapping("/{id}/profile") @Operation(summary="公开用户安全资料")
    public ApiResponse<PublicProfileVO> profile(@PathVariable String id) { return ApiResponse.success(users.publicProfile(id)); }
}
