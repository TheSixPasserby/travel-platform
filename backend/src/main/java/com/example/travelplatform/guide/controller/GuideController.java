package com.example.travelplatform.guide.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.guide.dto.*;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guides")
public class GuideController {
    private final GuideService guides;
    public GuideController(GuideService guides) { this.guides=guides; }
    @GetMapping @SecurityRequirements @Operation(summary="公开攻略搜索分页")
    public ApiResponse<PageResponse<GuideSummaryVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) String destinationId,
            @RequestParam(required=false) String tagId,@RequestParam(required=false) String sort) {
        return ApiResponse.success(guides.publicList(new GuideQuery(page,size,keyword,destinationId,tagId,sort)));
    }
    @GetMapping("/{id}") @SecurityRequirements @Operation(summary="公开攻略详情")
    public ApiResponse<GuideDetailVO> detail(@PathVariable String id) { return ApiResponse.success(guides.publicDetail(id)); }
    @GetMapping("/{id}/related") @SecurityRequirements @Operation(summary="同城市或同主题的公开攻略，排除当前文章，按城市优先、点赞、发布时间和ID排序")
    public ApiResponse<java.util.List<GuideSummaryVO>> related(@PathVariable String id,@RequestParam(required=false) String size) {
        return ApiResponse.success(guides.recommendations(id,size));
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="创建草稿或直接发布攻略")
    public ApiResponse<GuideDetailVO> create(@RequestBody CreateGuideRequest request) { return ApiResponse.success(guides.create(request)); }
    @PutMapping("/{id}") @Operation(summary="作者完整编辑攻略")
    public ApiResponse<GuideDetailVO> update(@PathVariable String id,@RequestBody UpdateGuideRequest request) { return ApiResponse.success(guides.update(id,request)); }
    @DeleteMapping("/{id}") @Operation(summary="作者逻辑删除攻略")
    public ApiResponse<Void> delete(@PathVariable String id) { guides.delete(id);return ApiResponse.success(null); }
    @PostMapping("/{id}/publish") @Operation(summary="作者发布草稿")
    public ApiResponse<GuideDetailVO> publish(@PathVariable String id) { return ApiResponse.success(guides.publish(id)); }
    @PostMapping("/{id}/unpublish") @Operation(summary="作者撤回攻略")
    public ApiResponse<GuideDetailVO> unpublish(@PathVariable String id) { return ApiResponse.success(guides.unpublish(id)); }
}
