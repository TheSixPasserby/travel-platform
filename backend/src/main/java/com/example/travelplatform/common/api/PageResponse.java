package com.example.travelplatform.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> records, long total, long current, long size, long pages) {
    public PageResponse { records = List.copyOf(records); }

    public static <E, T> PageResponse<T> from(IPage<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getRecords().stream().map(mapper).toList(), page.getTotal(),
                page.getCurrent(), page.getSize(), page.getPages());
    }
}
