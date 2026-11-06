package com.example.travelplatform.destination.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.destination.dto.DestinationImageRequest;
import com.example.travelplatform.destination.dto.DestinationWriteRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 目的地详情字段的纯策略：只做规范化与校验，不访问数据库、不抓取 URL、不碰磁盘。
 * 任何非法输入统一抛 {@link ErrorCode#VALIDATION_FAILED}，消息固定且不包含用户输入。
 */
public final class DestinationDetailsPolicy {

    /** 统一对外消息，禁止拼接输入内容。 */
    private static final String INVALID_MESSAGE = "目的地资料填写不合法";

    private static final Pattern COORDINATE_PATTERN =
            Pattern.compile("-?(0|[1-9][0-9]{0,2})(\\.[0-9]{1,6})?");

    private static final Pattern MANAGED_IMAGE_PATH_PATTERN =
            Pattern.compile("/uploads/[0-9]{4}/(0[1-9]|1[0-2])/[0-9a-f]{32}\\.(jpg|png|webp)");

    private static final DateTimeFormatter OFFSET_DATE_TIME =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withResolverStyle(ResolverStyle.STRICT);

    private static final ZoneOffset STORAGE_OFFSET = ZoneOffset.ofHours(8);

    private static final int MAX_TEXT_URL_LENGTH = 1000;
    private static final int MAX_ADDRESS_LENGTH = 300;
    private static final int MAX_DETAIL_TEXT_LENGTH = 1000;
    private static final int MAX_IMAGES = 20;
    private static final int MAX_ALT_TEXT_LENGTH = 200;
    private static final int MAX_AUTHOR_LENGTH = 300;
    private static final int MAX_LICENSE_LENGTH = 100;

    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 1440;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;

    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");

    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;

    private DestinationDetailsPolicy() {
    }

    /**
     * 规范化目的地详情字段。
     *
     * @param request 原始请求；非 null
     * @return 不可变的规范化结果；images 为 null 表示请求省略了 images（保留旧图库）
     * @throws ApiException 任一字段非法
     */
    public static DestinationDetailsValues normalize(DestinationWriteRequest request) {
        if (request == null) {
            throw invalid();
        }

        String address = optionalText(request.getAddress(), MAX_ADDRESS_LENGTH);
        String openingHours = optionalText(request.getOpeningHours(), MAX_DETAIL_TEXT_LENGTH);
        String ticketInfo = optionalText(request.getTicketInfo(), MAX_DETAIL_TEXT_LENGTH);
        String officialUrl = optionalUrl(request.getOfficialUrl());
        String sourceUrl = optionalUrl(request.getSourceUrl());

        BigDecimal longitude = null;
        BigDecimal latitude = null;
        String rawLongitude = normalizeOptionalRaw(request.getLongitude());
        String rawLatitude = normalizeOptionalRaw(request.getLatitude());
        if (rawLongitude != null && rawLatitude != null) {
            longitude = coordinate(rawLongitude, MAX_LONGITUDE, MIN_LONGITUDE);
            latitude = coordinate(rawLatitude, MAX_LATITUDE, MIN_LATITUDE);
        } else if (rawLongitude != null || rawLatitude != null) {
            throw invalid();
        }

        Integer recommendedDurationMinutes = boundedInteger(
                request.getRecommendedDurationMinutes(), MIN_DURATION_MINUTES, MAX_DURATION_MINUTES);
        Integer recommendedDays = boundedInteger(request.getRecommendedDays(), MIN_DAYS, MAX_DAYS);

        LocalDateTime verifiedAt = optionalVerifiedAt(request.getVerifiedAt());

        boolean hasCoordinates = longitude != null || latitude != null;
        boolean hasDetails = hasCoordinates || openingHours != null || ticketInfo != null;
        if (hasDetails && (sourceUrl == null || verifiedAt == null)) {
            throw invalid();
        }

        List<DestinationImageValues> images = request.detailProvided("images")
                ? images(request.getImages())
                : null;

        return new DestinationDetailsValues(
                address,
                longitude,
                latitude,
                recommendedDurationMinutes,
                recommendedDays,
                openingHours,
                ticketInfo,
                officialUrl,
                sourceUrl,
                verifiedAt,
                images);
    }

    // ------------------------------------------------------------------ 文本

    private static String optionalText(String raw, int maxCodePoints) {
        String text = stripText(raw);
        if (text == null) {
            return null;
        }
        if (codePointLength(text) > maxCodePoints) {
            throw invalid();
        }
        return text;
    }

    /** 仅 strip 后的原始可选文本（用于坐标，长度交给坐标正则）。 */
    private static String normalizeOptionalRaw(String raw) {
        String text = stripText(raw);
        return text == null ? null : text;
    }

    /**
     * CRLF 归一为 LF、strip、拒绝 C0/C1 控制字符（TAB/LF/CR 除外）；空白返回 null。
     */
    private static String stripText(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.indexOf('\r') >= 0 ? raw.replace("\r\n", "\n").replace('\r','\n') : raw;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (codePoint < 0x20 || (codePoint >= 0x7f && codePoint <= 0x9f)) {
                if (codePoint != '\t' && codePoint != '\n' && codePoint != '\r') {
                    throw invalid();
                }
            }
            i += Character.charCount(codePoint);
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static int codePointLength(String text) {
        return text.codePointCount(0, text.length());
    }

    // ------------------------------------------------------------------ 坐标

    private static BigDecimal coordinate(String raw, BigDecimal max, BigDecimal min) {
        if (!COORDINATE_PATTERN.matcher(raw).matches()) {
            throw invalid();
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw invalid();
        }
        return value;
    }

    // ------------------------------------------------------------------ 数字

    private static Integer boundedInteger(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw invalid();
        }
        return value;
    }

    // ------------------------------------------------------------------ URL

    private static String optionalUrl(String raw) {
        String text = stripText(raw);
        if (text == null) {
            return null;
        }
        if (codePointLength(text) > MAX_TEXT_URL_LENGTH) {
            throw invalid();
        }
        requireHttpUrl(text);
        return text;
    }

    private static void requireHttpUrl(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw invalid();
        }
        if (!uri.isAbsolute()) {
            throw invalid();
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw invalid();
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw invalid();
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid();
        }
    }

    // ------------------------------------------------------------------ 核验时间

    private static LocalDateTime optionalVerifiedAt(String raw) {
        String text = stripText(raw);
        if (text == null) {
            return null;
        }
        return toStorageLocalDateTime(text);
    }

    /**
     * 严格解析带偏移量的 ISO datetime，并换算到 +08:00 的 LocalDateTime。
     */
    private static LocalDateTime toStorageLocalDateTime(String text) {
        LocalDateTime converted;
        try {
            converted = OffsetDateTime.parse(text, OFFSET_DATE_TIME).atZoneSameInstant(STORAGE_OFFSET).toLocalDateTime();
        } catch (DateTimeException exception) {
            throw invalid();
        }
        if (converted.getYear() < MIN_YEAR || converted.getYear() > MAX_YEAR) {
            throw invalid();
        }
        return converted;
    }

    // ------------------------------------------------------------------ 图库

    private static List<DestinationImageValues> images(List<DestinationImageRequest> requests) {
        // 显式 null 非法：调用方需用 [] 表示清空
        if (requests == null) {
            throw invalid();
        }
        if (requests.size() > MAX_IMAGES) {
            throw invalid();
        }
        List<DestinationImageValues> images = new ArrayList<>(requests.size());
        Set<String> seenPaths = new HashSet<>();
        for (DestinationImageRequest request : requests) {
            if (request == null) {
                throw invalid();
            }
            DestinationImageValues image = image(request);
            if (!seenPaths.add(image.url())) {
                throw invalid();
            }
            images.add(image);
        }
        return List.copyOf(images);
    }

    private static DestinationImageValues image(DestinationImageRequest request) {
        String url = normalizeOptionalRaw(request.url());
        if (url == null || !MANAGED_IMAGE_PATH_PATTERN.matcher(url).matches()) {
            throw invalid();
        }
        String altText = requiredText(request.altText(), MAX_ALT_TEXT_LENGTH);
        String author = requiredText(request.author(), MAX_AUTHOR_LENGTH);
        String license = requiredText(request.license(), MAX_LICENSE_LENGTH);
        String sourceUrl = optionalUrl(request.sourceUrl());
        if (sourceUrl == null) {
            throw invalid();
        }
        requireHttpUrl(sourceUrl);
        String licenseUrl = optionalUrl(request.licenseUrl());
        if (licenseUrl == null) {
            throw invalid();
        }
        requireHttpUrl(licenseUrl);
        String retrievedAt = stripText(request.retrievedAt());
        if (retrievedAt == null) {
            throw invalid();
        }
        return new DestinationImageValues(
                url,
                altText,
                sourceUrl,
                author,
                license,
                licenseUrl,
                toStorageLocalDateTime(retrievedAt));
    }

    private static String requiredText(String raw, int maxCodePoints) {
        String text = optionalText(raw, maxCodePoints);
        if (text == null) {
            throw invalid();
        }
        return text;
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, INVALID_MESSAGE);
    }
}
