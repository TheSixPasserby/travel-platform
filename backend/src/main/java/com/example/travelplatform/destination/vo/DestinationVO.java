package com.example.travelplatform.destination.vo;

import com.example.travelplatform.destination.entity.Destination;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 目的地响应视图，只暴露白名单字段。
 */
public record DestinationVO(
        String id,
        String name,
        String destinationType,
        String parentId,
        String country,
        String province,
        String city,
        String description,
        String coverImageUrl,
        String recommendedSeasons,
        String status,
        int sortOrder,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+8")
        OffsetDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "GMT+8")
        OffsetDateTime updatedAt
) {

    private static final ZoneOffset DATABASE_OFFSET = ZoneOffset.ofHours(8);

    public static DestinationVO from(Destination destination) {
        if (destination == null) {
            return null;
        }
        return new DestinationVO(
                toId(destination.getId()),
                destination.getName(),
                destination.getDestinationType(),
                toId(destination.getParentId()),
                destination.getCountry(),
                destination.getProvince(),
                destination.getCity(),
                destination.getDescription(),
                destination.getCoverImageUrl(),
                destination.getRecommendedSeasons(),
                destination.getStatus(),
                destination.getSortOrder() == null ? 0 : destination.getSortOrder(),
                toOffsetDateTime(destination.getCreatedAt()),
                toOffsetDateTime(destination.getUpdatedAt())
        );
    }

    private static String toId(Long value) {
        return value == null ? null : value.toString();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(DATABASE_OFFSET);
    }
}
