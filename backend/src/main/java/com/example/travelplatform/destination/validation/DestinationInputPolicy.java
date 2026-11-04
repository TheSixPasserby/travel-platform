package com.example.travelplatform.destination.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.dto.DestinationWriteRequest;
import java.util.Set;

public final class DestinationInputPolicy {
    private static final Set<String> TYPES = Set.of("CITY", "SCENIC_SPOT", "OTHER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private DestinationInputPolicy() { }

    public static DestinationValues normalize(DestinationWriteRequest value) {
        if (value == null) throw invalid();
        if (value.getSortOrder() == null || value.getSortOrder() < 0) throw invalid();
        return new DestinationValues(required(value.getName(), 120), type(value.getDestinationType()),
                value.getParentId() == null ? null : RequestValues.positiveId(value.getParentId()),
                required(value.getCountry(), 80), optional(value.getProvince(), 80), optional(value.getCity(), 80),
                optional(value.getDescription(), 10_000), optional(value.getCoverImageUrl(), 500),
                optional(value.getRecommendedSeasons(), 120), status(value.getStatus()), value.getSortOrder());
    }
    public static String type(String value) {
        if (value == null || !TYPES.contains(value)) throw invalid();
        return value;
    }
    public static String status(String value) {
        if (value == null || !STATUSES.contains(value)) throw invalid();
        return value;
    }
    private static String required(String value, int maximum) {
        if (value == null || value.strip().isEmpty()) throw invalid();
        return optional(value.strip(), maximum);
    }
    private static String optional(String value, int maximum) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) throw invalid();
        return value;
    }
    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "目的地字段缺失、格式或长度不正确");
    }
}
