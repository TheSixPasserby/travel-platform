package com.example.travelplatform.itinerary.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.itinerary.dto.ItemWriteRequest;
import com.example.travelplatform.itinerary.service.ItineraryService;
import com.example.travelplatform.itinerary.vo.ItemVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/itinerary-items")
public class ItineraryItemController {
    private final ItineraryService service;
    public ItineraryItemController(ItineraryService service){this.service=service;}
    @PutMapping("/{id}") @Operation(summary="修改本人行程项目")
    public ApiResponse<ItemVO> update(@PathVariable String id,@RequestBody ItemWriteRequest body){return ApiResponse.success(service.updateItem(id,body));}
    @DeleteMapping("/{id}") @Operation(summary="删除本人行程项目")
    public ApiResponse<Void> delete(@PathVariable String id){service.deleteItem(id);return ApiResponse.success(null);}
}
