package com.example.travelplatform.itinerary.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** 行程日期与时间的严格校验工具: 非法输入统一抛 ApiException, 不回显原始值。 */
public final class ItineraryDates {

    private static final String INVALID_MESSAGE = "行程日期或时间格式不正确";
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;
    private static final int MAX_DAYS = 366;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private ItineraryDates() {
    }

    /** 闭区间日期范围, 两端均有效且 startDate <= endDate。 */
    public record Range(LocalDate startDate, LocalDate endDate) {
    }

    /** 可单独为空的起止时间, 均非空时 endTime >= startTime, 不推断跨夜。 */
    public record Times(LocalTime startTime, LocalTime endTime) {
    }

    /** 严格解析 yyyy-MM-dd, 年份限 1000..9999。 */
    public static LocalDate date(String value) {
        LocalDate parsed = parseDate(value);
        if (parsed.getYear() < MIN_YEAR || parsed.getYear() > MAX_YEAR) {
            throw invalid();
        }
        return parsed;
    }

    /** 解析闭区间范围, 最多 366 天。 */
    public static Range range(String start, String end) {
        LocalDate startDate = date(start);
        LocalDate endDate = date(end);
        requireValidRange(startDate, endDate);
        return new Range(startDate, endDate);
    }

    /** 行程第几天, 起始日为 1, 仅接受范围内的日期。 */
    public static int dayNo(LocalDate startDate, LocalDate endDate, LocalDate travelDate) {
        requireValidRange(startDate, endDate);
        if (travelDate == null || travelDate.isBefore(startDate) || travelDate.isAfter(endDate)) {
            throw invalid();
        }
        return (int) (ChronoUnit.DAYS.between(startDate, travelDate) + 1);
    }

    /** 严格解析 HH:mm, null 表示未填写。 */
    public static LocalTime time(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    /** 可空起止时间, 非空时 endTime >= startTime, 拒绝跨夜推断。 */
    public static Times times(String start, String end) {
        LocalTime startTime = time(start);
        LocalTime endTime = time(end);
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw invalid();
        }
        return new Times(startTime, endTime);
    }

    private static void requireValidRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null
                || startDate.getYear() < MIN_YEAR || startDate.getYear() > MAX_YEAR
                || endDate.getYear() < MIN_YEAR || endDate.getYear() > MAX_YEAR || endDate.isBefore(startDate)
                || ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_DAYS) {
            throw invalid();
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw invalid();
        }
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, INVALID_MESSAGE);
    }
}
