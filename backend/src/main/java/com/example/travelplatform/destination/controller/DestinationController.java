package com.example.travelplatform.destination.controller;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.destination.service.DestinationService;
import com.example.travelplatform.destination.vo.DestinationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/destinations")
@SecurityRequirements
public class DestinationController {
    private final DestinationService destinations;
    public DestinationController(DestinationService destinations) { this.destinations = destinations; }

    @GetMapping
    @Operation(summary = "分页浏览可用目的地")
    public ApiResponse<PageResponse<DestinationVO>> list(@RequestParam(required=false) String page,
            @RequestParam(required=false) String size, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String destinationType, @RequestParam(required=false) String parentId) {
        return ApiResponse.success(destinations.publicList(page, size, keyword, destinationType, parentId));
    }
    @GetMapping("/{id}")
    @Operation(summary = "目的地详情，含历史停用目的地")
    public ApiResponse<DestinationVO> detail(@PathVariable String id) { return ApiResponse.success(destinations.publicDetail(id)); }
}
