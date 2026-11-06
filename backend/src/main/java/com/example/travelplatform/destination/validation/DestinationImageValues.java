package com.example.travelplatform.destination.validation;

import java.time.LocalDateTime;

/**
 * 单张目的地图片的规范化结果。仅校验受管路径形状，不访问磁盘。
 */
public record DestinationImageValues(
        String url,
        String altText,
        String sourceUrl,
        String author,
        String license,
        String licenseUrl,
        LocalDateTime retrievedAt) {
}
