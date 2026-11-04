package com.example.travelplatform.guide.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.guide.dto.MyGuideQuery;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/guides")
public class MyGuidesController {
    private final GuideService guides;
    public MyGuidesController(GuideService guides) { this.guides=guides; }
    @GetMapping @Operation(summary="我的攻略分页，含草稿与隐藏状态")
    public ApiResponse<PageResponse<GuideSummaryVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String status) {
        return ApiResponse.success(guides.mine(new MyGuideQuery(page,size,keyword,status)));
    }
    @GetMapping("/{id}") @Operation(summary="作者私有攻略编辑数据")
    public ApiResponse<GuideDetailVO> detail(@PathVariable String id) { return ApiResponse.success(guides.ownDetail(id)); }
}
