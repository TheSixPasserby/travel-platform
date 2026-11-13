package com.example.travelplatform.search;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

public record SearchCursor(LocalDateTime snapshot, int score, LocalDateTime time, String kind, long id) {
    static final Set<String> KINDS = Set.of("GUIDE", "CITY", "SCENIC_SPOT", "CREATOR", "TAG");

    String encode(String key) {
        String value = String.join("|", snapshot.toString(), Integer.toString(score), time.toString(), kind, Long.toString(id), key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    static SearchCursor decode(String raw, String key, LocalDateTime now) {
        try {
            if (raw.length() > 1024) throw new IllegalArgumentException();
            String[] values = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8).split("\\|", -1);
            if (values.length != 6 || !key.equals(values[5])) throw new IllegalArgumentException();
            var cursor = new SearchCursor(LocalDateTime.parse(values[0]), Integer.parseInt(values[1]), LocalDateTime.parse(values[2]), values[3], Long.parseLong(values[4]));
            if (cursor.snapshot.isAfter(now.plusSeconds(1)) || cursor.time.isAfter(cursor.snapshot)
                    || cursor.score < 0 || cursor.score > 2 || cursor.id < 1 || !KINDS.contains(cursor.kind)) throw new IllegalArgumentException();
            return cursor;
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "搜索游标无效，请重新搜索");
        }
    }
}
