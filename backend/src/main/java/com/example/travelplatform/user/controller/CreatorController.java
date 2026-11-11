package com.example.travelplatform.user.controller;

import com.example.travelplatform.common.api.*;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.GuideSummaryVO;
import com.example.travelplatform.user.vo.CreatorProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/by-username/{username}")
@SecurityRequirements
public class CreatorController {
    private final GuideService guides;
    public CreatorController(GuideService guides){this.guides=guides;}
    @GetMapping @Operation(summary="公开创作者资料及已发布攻略数")
    public ApiResponse<CreatorProfileVO> profile(@PathVariable String username){return ApiResponse.success(guides.creatorProfile(username));}
    @GetMapping("/guides") @Operation(summary="创作者已发布攻略分页，按发布时间和ID倒序")
    public ApiResponse<PageResponse<GuideSummaryVO>> guides(@PathVariable String username,@RequestParam(required=false) String page,@RequestParam(required=false) String size){
        return ApiResponse.success(guides.publicAuthor(username,page,size));
    }
}
