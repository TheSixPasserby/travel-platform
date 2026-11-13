package com.example.travelplatform.search;

import com.example.travelplatform.common.api.FeedResponse;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.guide.service.FeedCursor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SearchService {
    private final SearchMapper mapper;
    public SearchService(SearchMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public FeedResponse<SearchHit> search(String rawKeyword, String kind, String rawSort, String rawSize, String rawCursor) {
        String keyword = RequestValues.keyword(rawKeyword);
        kind = kind == null || kind.isBlank() ? null : kind;
        rawCursor = rawCursor == null || rawCursor.isBlank() ? null : rawCursor;
        long size = RequestValues.size(rawSize == null ? "24" : rawSize);
        String sort = rawSort == null || rawSort.isBlank() ? "relevance" : rawSort;
        if ((kind != null && !SearchCursor.KINDS.contains(kind)) || !List.of("relevance", "latest").contains(sort))
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请选择有效的搜索分类和排序");
        String key = FeedCursor.fingerprint(keyword + "|" + kind + "|" + sort);
        var now = LocalDateTime.now();
        var cursor = rawCursor == null ? null : SearchCursor.decode(rawCursor, key, now);
        if (keyword == null) return new FeedResponse<>(List.of(), null, false);
        var snapshot = cursor == null ? now : cursor.snapshot();
        var rows = mapper.search(keyword, RequestValues.likePattern(keyword), kind, sort.equals("latest"), snapshot, cursor, size + 1);
        boolean more = rows.size() > size;
        var page = rows.subList(0, (int) Math.min(size, rows.size()));
        String next = null;
        if (more) {
            var last = page.getLast();
            next = new SearchCursor(snapshot, last.score(), last.resultTime(), last.kind(), last.id()).encode(key);
        }
        return new FeedResponse<>(page.stream().map(this::view).toList(), next, more);
    }
    private SearchHit view(SearchRow row) {
        String href = switch (row.kind()) {
            case "GUIDE" -> "/guides/" + row.id();
            case "CITY", "SCENIC_SPOT" -> "/destinations/" + row.id();
            case "CREATOR" -> "/users/" + URLEncoder.encode(row.username(), StandardCharsets.UTF_8);
            case "TAG" -> "/guides?tagId=" + row.id();
            default -> throw new IllegalStateException("Unknown search kind");
        };
        return new SearchHit(row.kind(), Long.toString(row.id()), row.title(), row.subtitle(), row.coverImageUrl(), row.username(), href);
    }
}
