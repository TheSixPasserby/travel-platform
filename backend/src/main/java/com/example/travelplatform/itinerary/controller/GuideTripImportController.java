package com.example.travelplatform.itinerary.controller;

import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.itinerary.dto.GuideTripImportRequest;
import com.example.travelplatform.itinerary.service.GuideTripImportService;
import com.example.travelplatform.itinerary.vo.GuideTripImportResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/itineraries/guide-imports")
public class GuideTripImportController {
    private final GuideTripImportService service;
    public GuideTripImportController(GuideTripImportService service) { this.service = service; }
    @PostMapping
    @Operation(summary = "将真实攻略关联景点原子加入本人行程，UUID幂等并跳过当天已有景点")
    public ApiResponse<GuideTripImportResult> save(@RequestBody GuideTripImportRequest body) { return ApiResponse.success(service.save(body)); }
}
