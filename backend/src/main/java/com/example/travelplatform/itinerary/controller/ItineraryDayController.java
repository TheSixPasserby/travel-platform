package com.example.travelplatform.itinerary.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.itinerary.dto.*;
import com.example.travelplatform.itinerary.service.ItineraryService;
import com.example.travelplatform.itinerary.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/itinerary-days")
public class ItineraryDayController {
    private final ItineraryService service;
    public ItineraryDayController(ItineraryService service){this.service=service;}
    @PutMapping("/{id}") @Operation(summary="修改本人的每日安排")
    public ApiResponse<DayVO> update(@PathVariable String id,@RequestBody DayWriteRequest body){return ApiResponse.success(service.updateDay(id,body));}
    @DeleteMapping("/{id}") @Operation(summary="删除本人日程及其项目")
    public ApiResponse<Void> delete(@PathVariable String id){service.deleteDay(id);return ApiResponse.success(null);}
    @PostMapping("/{id}/items") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="添加当天项目")
    public ApiResponse<ItemVO> item(@PathVariable String id,@RequestBody ItemWriteRequest body){return ApiResponse.success(service.createItem(id,body));}
    @PutMapping("/{id}/items/order") @Operation(summary="原子调整当天全部项目顺序")
    public ApiResponse<DayVO> order(@PathVariable String id,@RequestBody ItemOrderRequest body){return ApiResponse.success(service.order(id,body));}
}
