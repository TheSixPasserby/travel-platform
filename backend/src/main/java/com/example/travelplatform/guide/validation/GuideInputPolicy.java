package com.example.travelplatform.guide.validation;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.guide.dto.CreateGuideRequest;
import com.example.travelplatform.guide.dto.GuideImageRequest;
import com.example.travelplatform.guide.dto.GuideWriteRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 攻略写入参数的纯校验策略：只依赖请求对象，不读数据库、不访问磁盘。
 * 图片与标签是否真实存在由 Service 层另行校验。
 * 所有失败统一抛 {@link ApiException}（VALIDATION_FAILED + 通用中文提示），不回显原始输入值。
 */
public final class GuideInputPolicy {

    private static final String MESSAGE = "攻略填写内容不合法，请检查后重试";

    private static final int TITLE_MAX = 180;
    private static final int CONTENT_MAX = 100000;
    private static final int SUMMARY_MAX = 500;
    private static final int SEASON_MAX = 40;
    private static final int URL_MAX = 500;
    private static final int ALT_TEXT_MAX = 200;
    private static final int TAG_LIMIT = 10;
    private static final int IMAGE_LIMIT = 20;
    private static final int TRAVEL_DAYS_MIN = 1;
    private static final int TRAVEL_DAYS_MAX = 365;

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 金额语法：整数部分最多 8 位且无前导零，小数最多 2 位。 */
    private static final Pattern MONEY = Pattern.compile("^(0|[1-9][0-9]{0,7})(\\.[0-9]{1,2})?$");

    private GuideInputPolicy() {
    }

    /**
     * 归一化作者可编辑字段。文本长度按 Unicode 码点计算；title/summary/season 去除首尾空白，
     * content 保留 Markdown 原样（草稿允许为空），coverImageUrl 保留原始路径不裁剪。
     */
    public static GuideValues normalize(GuideWriteRequest request) {
        if (request == null) {
            throw invalid();
        }
        String title = requiredStripped(request.getTitle(), TITLE_MAX);
        String content = requiredContent(request.getContent());
        String summary = optionalStripped(request.getSummary(), SUMMARY_MAX);
        String season = optionalStripped(request.getSeason(), SEASON_MAX);
        Long destinationId = optionalId(request.getDestinationId());
        String coverImageUrl = optionalCover(request.getCoverImageUrl());
        Integer travelDays = optionalTravelDays(request.getTravelDays());
        BigDecimal budgetPerPerson = optionalMoney(request.getBudgetPerPerson());
        List<Long> tagIds = normalizeTagIds(request.getTagIds());
        List<GuideImageRequest> images = normalizeImages(request.getImages());
        return new GuideValues(title, content, summary, destinationId, coverImageUrl,
                travelDays, budgetPerPerson, season, tagIds, images);
    }

    /**
     * 创建时的目标状态：只接受 DRAFT 与 PUBLISHED（区分大小写），缺失即非法。
     * DTO 上的默认值由 DTO 维护，本策略不代为补默认值。
     */
    public static String creationStatus(CreateGuideRequest request) {
        if (request == null) {
            throw invalid();
        }
        String status = request.getStatus();
        if (STATUS_DRAFT.equals(status)) {
            return STATUS_DRAFT;
        }
        if (STATUS_PUBLISHED.equals(status)) {
            return STATUS_PUBLISHED;
        }
        throw invalid();
    }

    /** 发布语义要求正文非空白；空白正文可以保存为草稿。 */
    public static void requirePublishable(GuideValues values) {
        if (values == null || values.content() == null || values.content().isBlank()) {
            throw invalid();
        }
    }

    private static String requiredStripped(String raw, int maxCodePoints) {
        if (raw == null) {
            throw invalid();
        }
        String value = raw.strip();
        if (value.isEmpty() || codePoints(value) > maxCodePoints) {
            throw invalid();
        }
        return value;
    }

    private static String optionalStripped(String raw, int maxCodePoints) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (codePoints(value) > maxCodePoints) {
            throw invalid();
        }
        return value.isEmpty() ? null : value;
    }

    private static String requiredContent(String raw) {
        if (raw == null || codePoints(raw) > CONTENT_MAX) {
            throw invalid();
        }
        return raw;
    }

    private static Long optionalId(String raw) {
        return raw == null ? null : RequestValues.positiveId(raw);
    }

    private static String optionalCover(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank() || codePoints(raw) > URL_MAX) {
            throw invalid();
        }
        return raw;
    }

    private static Integer optionalTravelDays(Integer raw) {
        if (raw == null) {
            return null;
        }
        if (raw < TRAVEL_DAYS_MIN || raw > TRAVEL_DAYS_MAX) {
            throw invalid();
        }
        return raw;
    }

    private static BigDecimal optionalMoney(String raw) {
        if (raw == null) {
            return null;
        }
        if (!MONEY.matcher(raw).matches()) {
            throw invalid();
        }
        // 语法保证小数位不超过 2，setScale(2) 为精确扩位，不做舍入。
        return new BigDecimal(raw).setScale(2);
    }

    private static List<Long> normalizeTagIds(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > TAG_LIMIT) {
            throw invalid();
        }
        List<Long> parsed = new ArrayList<>(raw.size());
        Set<Long> seen = new HashSet<>();
        for (String item : raw) {
            if (item == null) {
                throw invalid();
            }
            // 用 Long 主键判重，避免 "1" 与 "01" 被当成不同标签。
            long id = RequestValues.positiveId(item);
            if (!seen.add(id)) {
                throw invalid();
            }
            parsed.add(id);
        }
        return List.copyOf(parsed);
    }

    private static List<GuideImageRequest> normalizeImages(List<GuideImageRequest> raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw.size() > IMAGE_LIMIT) {
            throw invalid();
        }
        List<GuideImageRequest> kept = new ArrayList<>(raw.size());
        Set<String> urls = new HashSet<>();
        for (GuideImageRequest image : raw) {
            if (image == null) {
                throw invalid();
            }
            String url = image.url();
            if (url == null || url.isBlank() || codePoints(url) > URL_MAX) {
                throw invalid();
            }
            if (!urls.add(url)) {
                throw invalid();
            }
            if (image.altText() != null && codePoints(image.altText()) > ALT_TEXT_MAX) {
                throw invalid();
            }
            kept.add(image);
        }
        return List.copyOf(kept);
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, MESSAGE);
    }
}
