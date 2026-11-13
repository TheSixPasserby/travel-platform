package com.example.travelplatform.comment.controller;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.comment.dto.CommentCreateRequest;
import com.example.travelplatform.comment.service.CommentService;
import com.example.travelplatform.comment.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class CommentController {
    private final CommentService comments;
    public CommentController(CommentService comments){this.comments=comments;}
    @GetMapping("/guides/{id}/comments") @SecurityRequirements @Operation(summary="公开评论分页")
    public ApiResponse<PageResponse<CommentVO>> list(@PathVariable String id,@RequestParam(required=false) String page,@RequestParam(required=false) String size){return ApiResponse.success(comments.list(id,page,size));}
    @GetMapping("/comments/{id}/replies") @SecurityRequirements @Operation(summary="根评论公开回复分页")
    public ApiResponse<PageResponse<CommentVO>> replies(@PathVariable String id,@RequestParam(required=false) String page,@RequestParam(required=false) String size){return ApiResponse.success(comments.replies(id,page,size));}
    @GetMapping("/comments/{id}/location") @SecurityRequirements @Operation(summary="公开评论分页位置")
    public ApiResponse<CommentService.Location> location(@PathVariable String id){return ApiResponse.success(comments.location(id));}
    @PostMapping("/guides/{id}/comments") @ResponseStatus(HttpStatus.CREATED) @Operation(summary="发表纯文本评论")
    public ApiResponse<CommentVO> create(@PathVariable String id,@RequestBody CommentCreateRequest request){return ApiResponse.success(comments.create(id,request));}
    @DeleteMapping("/comments/{id}") @Operation(summary="作者删除本人评论")
    public ApiResponse<Void> delete(@PathVariable String id){comments.delete(id);return ApiResponse.success(null);}
}
