package com.example.travelplatform.administration.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.administration.service.AdministrationService;
import com.example.travelplatform.administration.vo.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdministrationService service;
    public AdminUserController(AdministrationService service){this.service=service;}
    @GetMapping @Operation(summary="管理员安全用户分页")
    public ApiResponse<PageResponse<AdminUserVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String role){return ApiResponse.success(service.users(page,size,keyword,status,role));}
    @PatchMapping("/{id}/status") @Operation(summary="管理员启用或禁用用户")
    public ApiResponse<AdminUserVO> status(@PathVariable String id,@RequestBody StatusPatchRequest request){return ApiResponse.success(service.userStatus(id,request));}
}
