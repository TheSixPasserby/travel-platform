package com.example.travelplatform.comment.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.comment.service.CommentService;
import com.example.travelplatform.comment.vo.AdminCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/comments")
public class AdminCommentController {
    private final CommentService service;
    public AdminCommentController(CommentService service){this.service=service;}
    @GetMapping @Operation(summary="管理员评论分页治理")
    public ApiResponse<PageResponse<AdminCommentVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status){return ApiResponse.success(service.adminList(page,size,keyword,status));}
    @PatchMapping("/{id}/status") @Operation(summary="隐藏或恢复未删除评论")
    public ApiResponse<AdminCommentVO> status(@PathVariable String id,@RequestBody StatusPatchRequest request){return ApiResponse.success(service.adminStatus(id,request));}
}
