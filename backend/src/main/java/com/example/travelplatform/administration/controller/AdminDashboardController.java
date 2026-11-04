package com.example.travelplatform.administration.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.administration.service.AdministrationService;
import com.example.travelplatform.administration.vo.AdminSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private final AdministrationService service;
    public AdminDashboardController(AdministrationService service){this.service=service;}
    @GetMapping("/summary") @Operation(summary="管理员后台记录总览")
    public ApiResponse<AdminSummaryVO> summary(){return ApiResponse.success(service.summary());}
}
