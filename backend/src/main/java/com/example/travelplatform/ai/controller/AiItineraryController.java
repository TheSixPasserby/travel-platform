package com.example.travelplatform.ai.controller;

import com.example.travelplatform.ai.dto.*;
import com.example.travelplatform.ai.service.AiItineraryService;
import com.example.travelplatform.ai.vo.AiPreview;
import com.example.travelplatform.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/itineraries")
public class AiItineraryController {
    private final AiItineraryService service;
    public AiItineraryController(AiItineraryService service) { this.service=service; }
    @GetMapping("/capabilities")
    public ApiResponse<AiPreview.Capabilities> capabilities() { return ApiResponse.success(service.capabilities()); }
    @PostMapping("/preview")
    public ApiResponse<AiPreview> preview(@RequestBody AiRequirements request) { return ApiResponse.success(service.preview(request)); }
    @PostMapping("/day-preview")
    public ApiResponse<AiPreview.DayPreview> day(@RequestBody AiDayRequest request) { return ApiResponse.success(service.day(request)); }
}
