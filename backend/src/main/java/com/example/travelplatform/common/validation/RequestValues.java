package com.example.travelplatform.common.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;

public final class RequestValues {
    private RequestValues() { }

    public static long positiveId(String value) {
        if (value == null || !value.matches("[0-9]+")) throw invalid();
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw invalid();
            return parsed;
        } catch (NumberFormatException exception) { throw invalid(); }
    }

    public static long page(String value) { return value == null ? 1 : positiveId(value); }
    public static long size(String value) {
        long size = value == null ? 10 : positiveId(value);
        if (size > 50) throw invalid();
        return size;
    }
    public static long offset(long page, long size) {
        if (page < 1 || size < 1 || size > 50) throw invalid();
        try { return Math.multiplyExact(page - 1, size); }
        catch (ArithmeticException exception) { throw invalid(); }
    }
    public static String keyword(String raw) {
        if (raw == null) return null;
        String value = raw.strip();
        if (value.codePointCount(0, value.length()) > 100) throw invalid();
        return value.isEmpty() ? null : value;
    }
    public static String likePattern(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "ID、分页或关键词参数格式不正确");
    }
}
