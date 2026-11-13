package com.example.travelplatform.search;

import com.example.travelplatform.common.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@SecurityRequirements
public class SearchController {
    private final SearchService service;
    public SearchController(SearchService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "公开攻略、城市、景点、作者和话题的跨类型搜索，游标连续加载")
    public ApiResponse<FeedResponse<SearchHit>> search(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String kind, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String size, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(service.search(keyword, kind, sort, size, cursor));
    }
}
