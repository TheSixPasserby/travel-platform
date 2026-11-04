package com.example.travelplatform.guide.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.GuideSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/guides")
public class AdminGuideController {
    private final GuideService service;
    public AdminGuideController(GuideService service){this.service=service;}
    @GetMapping @Operation(summary="管理员攻略分页治理")
    public ApiResponse<PageResponse<GuideSummaryVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return ApiResponse.success(service.adminList(page,size,keyword,status));}
    @PatchMapping("/{id}/status") @Operation(summary="隐藏或恢复已发布攻略")
    public ApiResponse<GuideSummaryVO> status(@PathVariable String id,@RequestBody StatusPatchRequest request){return ApiResponse.success(service.adminStatus(id,request));}
}
