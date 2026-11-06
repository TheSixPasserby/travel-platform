package com.example.travelplatform.destination.validation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 目的地详情写入前的规范化结果。字段已 strip、CRLF 归一、长度与格式校验完毕；
 * images 为 null 表示请求未包含 images 字段（保留旧图库）。
 */
public record DestinationDetailsValues(
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        Integer recommendedDurationMinutes,
        Integer recommendedDays,
        String openingHours,
        String ticketInfo,
        String officialUrl,
        String sourceUrl,
        LocalDateTime verifiedAt,
        List<DestinationImageValues> images) {
}
