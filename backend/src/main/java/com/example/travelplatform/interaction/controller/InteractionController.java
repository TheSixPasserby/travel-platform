package com.example.travelplatform.interaction.controller;
import com.example.travelplatform.common.api.ApiResponse;
import com.example.travelplatform.interaction.service.InteractionService;
import com.example.travelplatform.interaction.vo.GuideInteractionVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/guides/{id}")
public class InteractionController {
    private final InteractionService interactions;
    public InteractionController(InteractionService interactions){this.interactions=interactions;}
    @GetMapping("/interaction") @Operation(summary="当前用户攻略互动状态")
    public ApiResponse<GuideInteractionVO> state(@PathVariable String id){return ApiResponse.success(interactions.state(id));}
    @PutMapping("/like") @Operation(summary="幂等点赞")
    public ApiResponse<GuideInteractionVO> like(@PathVariable String id){return ApiResponse.success(interactions.like(id,true));}
    @DeleteMapping("/like") @Operation(summary="幂等取消点赞")
    public ApiResponse<GuideInteractionVO> unlike(@PathVariable String id){return ApiResponse.success(interactions.like(id,false));}
    @PutMapping("/favorite") @Operation(summary="幂等收藏")
    public ApiResponse<GuideInteractionVO> favorite(@PathVariable String id){return ApiResponse.success(interactions.favorite(id,true));}
    @DeleteMapping("/favorite") @Operation(summary="幂等取消收藏")
    public ApiResponse<GuideInteractionVO> unfavorite(@PathVariable String id){return ApiResponse.success(interactions.favorite(id,false));}
}
