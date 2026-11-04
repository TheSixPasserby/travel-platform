package com.example.travelplatform.itinerary.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.itinerary.dto.*;
import com.example.travelplatform.itinerary.service.ItineraryService;
import com.example.travelplatform.itinerary.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/itineraries")
public class ItineraryController {
    private final ItineraryService service;
    public ItineraryController(ItineraryService service){this.service=service;}
    @GetMapping @Operation(summary="我的行程分页")
    public ApiResponse<PageResponse<ItinerarySummaryVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return ApiResponse.success(service.list(page,size,keyword,status));}
    @GetMapping("/{id}") @Operation(summary="本人行程详情")
    public ApiResponse<ItineraryDetailVO> get(@PathVariable String id){return ApiResponse.success(service.get(id));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary="创建私人行程")
    public ApiResponse<ItineraryDetailVO> create(@RequestBody ItineraryCreateRequest body){return ApiResponse.success(service.create(body));}
    @PutMapping("/{id}") @Operation(summary="完整修改本人行程")
    public ApiResponse<ItineraryDetailVO> update(@PathVariable String id,@RequestBody ItineraryUpdateRequest body){return ApiResponse.success(service.update(id,body));}
    @DeleteMapping("/{id}") @Operation(summary="逻辑删除本人行程")
    public ApiResponse<Void> delete(@PathVariable String id){service.delete(id);return ApiResponse.success(null);}
    @PostMapping("/{id}/days") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="添加每日安排")
    public ApiResponse<DayVO> day(@PathVariable String id,@RequestBody DayWriteRequest body){return ApiResponse.success(service.createDay(id,body));}
}
