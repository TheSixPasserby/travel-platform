package com.example.travelplatform.guide.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.guide.service.GuideService;
import com.example.travelplatform.guide.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/tags") @SecurityRequirements
public class TagController {
    private final GuideService guides;
    public TagController(GuideService guides) { this.guides=guides; }
    @GetMapping @Operation(summary="公开种子标签")
    public ApiResponse<List<TagVO>> list() { return ApiResponse.success(guides.tags()); }
}
