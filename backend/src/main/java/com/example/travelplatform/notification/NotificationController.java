package com.example.travelplatform.notification;
import com.example.travelplatform.common.api.*;
import com.example.travelplatform.user.vo.PublicProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    @GetMapping("/notifications") @Operation(summary="当前用户站内通知分页")
    public ApiResponse<PageResponse<NotificationService.NotificationVO>> list(@RequestParam(required=false) String page,@RequestParam(required=false) String size,@RequestParam(required=false) String unreadOnly) {
        return ApiResponse.success(service.list(page,size,unreadOnly));
    }
    @GetMapping("/notifications/unread-count") @Operation(summary="当前用户可见通知未读数")
    public ApiResponse<NotificationService.Unread> unread() { return ApiResponse.success(service.unread()); }
    @PutMapping("/notifications/{id}/read") @Operation(summary="当前用户标记通知已读")
    public ApiResponse<Void> mark(@PathVariable String id) { service.mark(id);return ApiResponse.success(null); }
    @PutMapping("/notifications/read-all") @Operation(summary="当前用户标记全部已读")
    public ApiResponse<Void> markAll() { service.markAll();return ApiResponse.success(null); }
    @GetMapping("/users/mention-suggestions") @Operation(summary="提及用户公开字段候选")
    public ApiResponse<List<PublicProfileVO>> suggestions(@RequestParam(required=false) String keyword) { return ApiResponse.success(service.suggestions(keyword)); }
}
