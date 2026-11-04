package com.example.travelplatform.destination.controller;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.destination.dto.*;
import com.example.travelplatform.destination.service.DestinationService;
import com.example.travelplatform.destination.vo.DestinationVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/destinations")
public class AdminDestinationController {
    private final DestinationService destinations;
    public AdminDestinationController(DestinationService destinations) { this.destinations = destinations; }

    @GetMapping
    @Operation(summary = "管理员分页维护目的地")
    public ApiResponse<PageResponse<DestinationVO>> list(@RequestParam(required=false) String page,
            @RequestParam(required=false) String size, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) String destinationType, @RequestParam(required=false) String parentId,
            @RequestParam(required=false) String status) {
        return ApiResponse.success(destinations.adminList(page, size, keyword, destinationType, parentId, status));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "管理员新增目的地")
    public ApiResponse<DestinationVO> create(@RequestBody CreateDestinationRequest request) {
        return ApiResponse.success(destinations.create(request));
    }
    @PutMapping("/{id}")
    @Operation(summary = "管理员完整修改目的地")
    public ApiResponse<DestinationVO> update(@PathVariable String id, @RequestBody DestinationWriteRequest request) {
        return ApiResponse.success(destinations.update(id, request));
    }
    @PatchMapping("/{id}/status")
    @Operation(summary = "管理员启用或停用目的地")
    public ApiResponse<DestinationVO> status(@PathVariable String id, @RequestBody DestinationStatusRequest request) {
        return ApiResponse.success(destinations.updateStatus(id, request));
    }
}
