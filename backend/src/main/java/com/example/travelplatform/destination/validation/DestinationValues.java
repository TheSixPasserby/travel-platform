package com.example.travelplatform.destination.validation;

/**
 * 校验并解析后的目的地写入值。
 */
public record DestinationValues(
        String name,
        String destinationType,
        Long parentId,
        String country,
        String province,
        String city,
        String description,
        String coverImageUrl,
        String recommendedSeasons,
        String status,
        int sortOrder
) {
}
