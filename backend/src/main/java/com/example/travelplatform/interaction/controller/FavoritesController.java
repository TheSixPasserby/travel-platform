package com.example.travelplatform.interaction.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.GuideSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me/favorites")
public class FavoritesController {
    private final GuideService guides;
    public FavoritesController(GuideService guides){this.guides=guides;}
    @GetMapping @Operation(summary="我的公开可见收藏分页")
    public ApiResponse<PageResponse<GuideSummaryVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String keyword){
        return ApiResponse.success(guides.favorites(page,size,keyword));
    }
}
