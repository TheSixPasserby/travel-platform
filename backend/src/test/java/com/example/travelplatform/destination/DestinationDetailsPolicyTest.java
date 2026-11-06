package com.example.travelplatform.destination;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.destination.dto.DestinationImageRequest;
import com.example.travelplatform.destination.dto.DestinationWriteRequest;
import com.example.travelplatform.destination.validation.DestinationDetailsPolicy;
import com.example.travelplatform.destination.validation.DestinationDetailsValues;
import com.example.travelplatform.destination.validation.DestinationImageValues;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目的地详情字段纯策略测试：不接 DB、不发请求，只验证规范化与校验规则。
 */
class DestinationDetailsPolicyTest {

    private static final String GOOD_SOURCE = "https://example.com/source";
    private static final String GOOD_VERIFIED_AT = "2024-01-02T03:04:05+08:00";
    private static final String LICENSE = "CC BY 4.0";
    private static final String LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/";
    private static final String RETRIEVED_AT = "2024-01-02T03:04:05+08:00";

    private static DestinationWriteRequest empty() {
        return new DestinationWriteRequest();
    }

    private static DestinationWriteRequest withSourceAndVerification() {
        DestinationWriteRequest request = new DestinationWriteRequest();
        request.setSourceUrl(GOOD_SOURCE);
        request.setVerifiedAt(GOOD_VERIFIED_AT);
        return request;
    }

    private static DestinationWriteRequest withCoordinates(String longitude, String latitude) {
        DestinationWriteRequest request = withSourceAndVerification();
        request.setLongitude(longitude);
        request.setLatitude(latitude);
        return request;
    }

    private static String managedPath(int seed, String extension) {
        return "/uploads/2024/" + String.format("%02d", (seed % 12) + 1) + "/"
                + String.format("%032x", (long) seed + 1L) + "." + extension;
    }

    private static String managedPath(int seed) {
        return managedPath(seed, "jpg");
    }

    private static DestinationImageRequest image(String url, String altText, String author,
                                                 String license, String sourceUrl,
                                                 String licenseUrl, String retrievedAt) {
        return new DestinationImageRequest(url, altText, sourceUrl, author, license, licenseUrl, retrievedAt);
    }

    private static DestinationImageRequest validImage() {
        return image(managedPath(0), "外滩夜景", "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT);
    }

    private static DestinationImageRequest validImage(String url) {
        return image(url, "外滩夜景", "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT);
    }

    private static DestinationWriteRequest withImages(DestinationImageRequest... images) {
        DestinationWriteRequest request = empty();
        List<DestinationImageRequest> list = new ArrayList<>();
        for (DestinationImageRequest image : images) {
            list.add(image);
        }
        request.setImages(list);
        return request;
    }

    private static ApiException invalid(DestinationWriteRequest request) {
        return assertThrows(ApiException.class, () -> DestinationDetailsPolicy.normalize(request));
    }

    private static void assertRejected(DestinationWriteRequest request) {
        ApiException exception = invalid(request);
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        assertEquals("目的地资料填写不合法", exception.getMessage());
    }

    private static DestinationDetailsValues normalize(DestinationWriteRequest request) {
        return assertDoesNotThrow(() -> DestinationDetailsPolicy.normalize(request));
    }

    private static void assertCoordinate(BigDecimal actual, String expected) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    // ---------------------------------------------------------------- 文本基础

    @Test
    @DisplayName("空请求：所有可选字段为 null，images 省略保留旧图库")
    void emptyRequestNormalizesToNulls() {
        DestinationDetailsValues values = normalize(empty());

        assertNull(values.address());
        assertNull(values.longitude());
        assertNull(values.latitude());
        assertNull(values.recommendedDurationMinutes());
        assertNull(values.recommendedDays());
        assertNull(values.openingHours());
        assertNull(values.ticketInfo());
        assertNull(values.officialUrl());
        assertNull(values.sourceUrl());
        assertNull(values.verifiedAt());
        assertNull(values.images());
    }

    @Test
    @DisplayName("空白可选文本归一为 null")
    void blankTextsBecomeNull() {
        DestinationWriteRequest request = empty();
        request.setAddress("   ");
        request.setOpeningHours("\t \n");
        request.setTicketInfo("");
        request.setOfficialUrl("  ");
        request.setSourceUrl("   ");

        DestinationDetailsValues values = normalize(request);

        assertNull(values.address());
        assertNull(values.openingHours());
        assertNull(values.ticketInfo());
        assertNull(values.officialUrl());
        assertNull(values.sourceUrl());
    }

    @Test
    @DisplayName("文本首尾 strip，CRLF 归一为 LF，TAB/LF 允许")
    void textIsStrippedAndCrlfNormalized() {
        DestinationWriteRequest request = withSourceAndVerification();
        request.setAddress("  上海市黄浦区中山东一路  ");
        request.setOpeningHours("  09:00-17:00\r\n周一闭馆  ");
        request.setTicketInfo("免费\t需预约\n限流");

        DestinationDetailsValues values = normalize(request);

        assertEquals("上海市黄浦区中山东一路", values.address());
        assertEquals("09:00-17:00\n周一闭馆", values.openingHours());
        assertEquals("免费\t需预约\n限流", values.ticketInfo());
    }

    @Test
    @DisplayName("C0/C1 控制字符拒绝（TAB/LF/CR 除外）")
    void controlCharactersAreRejected() {
        for (String bad : List.of("a\u0000b", "a\u0007b", "a\u001fb", "a\u007fb", "a\u0085b", "a\u009fb")) {
            DestinationWriteRequest badAddress = empty();
            badAddress.setAddress(bad);
            assertRejected(badAddress);
        }

        DestinationWriteRequest verticalTab = empty();
        verticalTab.setOpeningHours("a\u000bb");
        assertRejected(verticalTab);

        DestinationWriteRequest ticketControl = withSourceAndVerification();
        ticketControl.setTicketInfo("a\u0001b");
        assertRejected(ticketControl);
    }

    @Test
    @DisplayName("address 长度按 codepoint 计算：300 允许，301 拒绝")
    void addressLengthIsCountedInCodePoints() {
        DestinationWriteRequest ok = empty();
        ok.setAddress("😀".repeat(300));
        assertEquals("😀".repeat(300), normalize(ok).address());

        DestinationWriteRequest tooLong = empty();
        tooLong.setAddress("😀".repeat(301));
        assertRejected(tooLong);

        DestinationWriteRequest ascii = empty();
        ascii.setAddress("a".repeat(300));
        assertEquals("a".repeat(300), normalize(ascii).address());
    }

    @Test
    @DisplayName("openingHours/ticketInfo 各 1000 codepoint 上限")
    void detailTextLengthLimits() {
        DestinationWriteRequest ok = withSourceAndVerification();
        ok.setOpeningHours("a".repeat(1000));
        ok.setTicketInfo("b".repeat(1000));
        DestinationDetailsValues values = normalize(ok);
        assertEquals(1000, values.openingHours().length());
        assertEquals(1000, values.ticketInfo().length());

        DestinationWriteRequest hoursTooLong = withSourceAndVerification();
        hoursTooLong.setOpeningHours("a".repeat(1001));
        assertRejected(hoursTooLong);

        DestinationWriteRequest ticketTooLong = withSourceAndVerification();
        ticketTooLong.setTicketInfo("b".repeat(1001));
        assertRejected(ticketTooLong);
    }

    // ---------------------------------------------------------------- 坐标

    @Test
    @DisplayName("坐标合法范围与边界")
    void coordinatesAcceptedAtBoundaries() {
        DestinationDetailsValues normal = normalize(withCoordinates("121.473701", "31.230416"));
        assertCoordinate(normal.longitude(), "121.473701");
        assertCoordinate(normal.latitude(), "31.230416");

        DestinationDetailsValues max = normalize(withCoordinates("180", "90"));
        assertCoordinate(max.longitude(), "180");
        assertCoordinate(max.latitude(), "90");

        DestinationDetailsValues min = normalize(withCoordinates("-180", "-90"));
        assertCoordinate(min.longitude(), "-180");
        assertCoordinate(min.latitude(), "-90");

        DestinationDetailsValues zero = normalize(withCoordinates("0", "-0"));
        assertCoordinate(zero.longitude(), "0");
        assertCoordinate(zero.latitude(), "0");

        DestinationDetailsValues precision = normalize(withCoordinates("0.000001", "-0.000001"));
        assertCoordinate(precision.longitude(), "0.000001");
        assertCoordinate(precision.latitude(), "-0.000001");
    }

    @Test
    @DisplayName("坐标超出范围拒绝")
    void coordinatesOutOfRangeRejected() {
        for (String longitude : List.of("180.000001", "-180.1", "181", "1000")) {
            assertRejected(withCoordinates(longitude, "10"));
        }
        for (String latitude : List.of("90.000001", "-90.000001", "91")) {
            assertRejected(withCoordinates("10", latitude));
        }
    }

    @Test
    @DisplayName("坐标精度超过 6 位小数拒绝，不舍入")
    void coordinatePrecisionOverLimitRejected() {
        assertRejected(withCoordinates("120.1234567", "30.1"));
        assertRejected(withCoordinates("120.1", "30.1234567"));
        assertEquals("120.123456", normalize(withCoordinates("120.123456", "30.123456")).longitude().toPlainString());
    }

    @Test
    @DisplayName("坐标格式非法拒绝")
    void malformedCoordinatesRejected() {
        for (String bad : List.of("0120.1", "+120.1", "120.", ".5", "1e3", "120,5", "abc", "1.2.3", "--1", "120.1234567")) {
            assertRejected(withCoordinates(bad, "30"));
        }
    }

    @Test
    @DisplayName("坐标必须成对出现")
    void coordinatesMustBePaired() {
        DestinationWriteRequest onlyLongitude = withSourceAndVerification();
        onlyLongitude.setLongitude("121.1");
        assertRejected(onlyLongitude);

        DestinationWriteRequest onlyLatitude = withSourceAndVerification();
        onlyLatitude.setLatitude("31.1");
        assertRejected(onlyLatitude);

        DestinationWriteRequest blankAndValue = withSourceAndVerification();
        blankAndValue.setLongitude("   ");
        blankAndValue.setLatitude("31.1");
        assertRejected(blankAndValue);
    }

    @Test
    @DisplayName("坐标同时为空白视为未提供")
    void blankCoordinatesTreatedAsAbsent() {
        DestinationWriteRequest request = empty();
        request.setLongitude("  ");
        request.setLatitude("");

        DestinationDetailsValues values = normalize(request);

        assertNull(values.longitude());
        assertNull(values.latitude());
    }

    // ---------------------------------------------------------------- 时长/天数

    @Test
    @DisplayName("游玩时长 1-1440、天数 1-30，可空")
    void durationAndDaysRanges() {
        DestinationDetailsValues values = normalize(empty());
        assertNull(values.recommendedDurationMinutes());
        assertNull(values.recommendedDays());

        DestinationWriteRequest min = empty();
        min.setRecommendedDurationMinutes(1);
        min.setRecommendedDays(1);
        assertEquals(1, normalize(min).recommendedDurationMinutes());
        assertEquals(1, normalize(min).recommendedDays());

        DestinationWriteRequest max = empty();
        max.setRecommendedDurationMinutes(1440);
        max.setRecommendedDays(30);
        assertEquals(1440, normalize(max).recommendedDurationMinutes());
        assertEquals(30, normalize(max).recommendedDays());

        for (int bad : new int[]{0, -1, 1441, 5000}) {
            DestinationWriteRequest request = empty();
            request.setRecommendedDurationMinutes(bad);
            assertRejected(request);
        }
        for (int bad : new int[]{0, -1, 31, 100}) {
            DestinationWriteRequest request = empty();
            request.setRecommendedDays(bad);
            assertRejected(request);
        }
    }

    // ---------------------------------------------------------------- URL

    @Test
    @DisplayName("URL 仅接受 http/https 绝对地址，拒绝危险 scheme / userinfo / 控制字符")
    void urlValidation() {
        DestinationWriteRequest ok = empty();
        ok.setOfficialUrl("https://official.example.com/visit?lang=zh#top");
        ok.setSourceUrl("http://example.com/source");
        DestinationDetailsValues values = normalize(ok);
        assertEquals("https://official.example.com/visit?lang=zh#top", values.officialUrl());
        assertEquals("http://example.com/source", values.sourceUrl());

        DestinationWriteRequest upper = empty();
        upper.setOfficialUrl("HTTP://Example.COM/path");
        assertEquals("HTTP://Example.COM/path", normalize(upper).officialUrl());

        for (String bad : List.of(
                "ftp://example.com/file",
                "javascript:alert(1)",
                "data:text/html;base64,PHNjcmlwdD4=",
                "file:///etc/passwd",
                "https://user:pass@example.com/x",
                "https://user@example.com/x",
                "example.com/x",
                "//example.com/x",
                "/uploads/2024/01/x.jpg",
                "http://",
                "https://exa mple.com/x",
                "https://example.com/a\u0000b")) {
            DestinationWriteRequest request = empty();
            request.setOfficialUrl(bad);
            assertRejected(request);
        }
    }

    @Test
    @DisplayName("URL 长度上限 1000 codepoint")
    void urlLengthLimit() {
        String prefix = "https://example.com/";
        DestinationWriteRequest ok = empty();
        ok.setOfficialUrl(prefix + "a".repeat(1000 - prefix.length()));
        assertEquals(1000, normalize(ok).officialUrl().length());

        DestinationWriteRequest tooLong = empty();
        tooLong.setOfficialUrl(prefix + "a".repeat(1001 - prefix.length()));
        assertRejected(tooLong);
    }

    // ---------------------------------------------------------------- 来源与核验

    @Test
    @DisplayName("非空详情/坐标必须同时有 sourceUrl 与 verifiedAt")
    void detailsRequireSourceAndVerification() {
        DestinationWriteRequest coordsNoSource = empty();
        coordsNoSource.setLongitude("121.1");
        coordsNoSource.setLatitude("31.1");
        assertRejected(coordsNoSource);

        DestinationWriteRequest coordsOnlySource = empty();
        coordsOnlySource.setLongitude("121.1");
        coordsOnlySource.setLatitude("31.1");
        coordsOnlySource.setSourceUrl(GOOD_SOURCE);
        assertRejected(coordsOnlySource);

        DestinationWriteRequest coordsOnlyVerified = empty();
        coordsOnlyVerified.setLongitude("121.1");
        coordsOnlyVerified.setLatitude("31.1");
        coordsOnlyVerified.setVerifiedAt(GOOD_VERIFIED_AT);
        assertRejected(coordsOnlyVerified);

        DestinationWriteRequest openingHoursNoSource = empty();
        openingHoursNoSource.setOpeningHours("09:00-17:00");
        assertRejected(openingHoursNoSource);

        DestinationWriteRequest ticketInfoNoVerified = empty();
        ticketInfoNoVerified.setTicketInfo("免费");
        ticketInfoNoVerified.setSourceUrl(GOOD_SOURCE);
        assertRejected(ticketInfoNoVerified);

        DestinationWriteRequest ok = withCoordinates("121.1", "31.1");
        ok.setOpeningHours("09:00-17:00");
        ok.setTicketInfo("免费");
        normalize(ok);

        // 坐标为空时，纯 sourceUrl/verifiedAt 不构成详情，仍可接受
        DestinationWriteRequest sourceOnly = empty();
        sourceOnly.setSourceUrl(GOOD_SOURCE);
        sourceOnly.setVerifiedAt(GOOD_VERIFIED_AT);
        normalize(sourceOnly);
    }

    @Test
    @DisplayName("verifiedAt 严格 ISO offset datetime，并换算到 +08:00")
    void verifiedAtStrictOffsetParsing() {
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), normalize(withSourceAndVerification()).verifiedAt());

        DestinationWriteRequest utc = empty();
        utc.setVerifiedAt("2024-01-01T19:04:05Z");
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), normalize(utc).verifiedAt());

        DestinationWriteRequest zeroOffset = empty();
        zeroOffset.setVerifiedAt("2024-01-02T00:00:00+00:00");
        assertEquals(LocalDateTime.of(2024, 1, 2, 8, 0, 0), normalize(zeroOffset).verifiedAt());

        for (String bad : List.of(
                "2024-01-02",
                "2024-01-02T03:04:05",
                "2024-02-30T03:04:05+08:00",
                "0999-01-02T03:04:05+08:00",
                "2024/01/02T03:04:05+08:00",
                "not-a-time")) {
            DestinationWriteRequest request = empty();
            request.setVerifiedAt(bad);
            assertRejected(request);
        }
    }

    // ---------------------------------------------------------------- 图库

    @Test
    @DisplayName("images 省略为 null（保留旧图库）")
    void imagesOmittedMeansNull() {
        assertNull(normalize(empty()).images());

        DestinationWriteRequest request = empty();
        request.setAddress("上海");
        assertNull(normalize(request).images());
        assertFalse(request.detailProvided("images"));
    }

    @Test
    @DisplayName("images 显式 null 非法")
    void imagesExplicitNullRejected() {
        DestinationWriteRequest request = empty();
        request.setImages(null);
        assertTrue(request.detailProvided("images"));
        assertRejected(request);
    }

    @Test
    @DisplayName("images 空数组允许清空")
    void imagesEmptyListAllowed() {
        DestinationDetailsValues values = normalize(withImages());
        assertNotNull(values.images());
        assertTrue(values.images().isEmpty());
    }

    @Test
    @DisplayName("images 最多 20 张")
    void imagesMaxTwenty() {
        DestinationImageRequest[] twenty = new DestinationImageRequest[20];
        for (int i = 0; i < twenty.length; i++) {
            twenty[i] = validImage(managedPath(i));
        }
        assertEquals(20, normalize(withImages(twenty)).images().size());

        DestinationImageRequest[] twentyOne = new DestinationImageRequest[21];
        for (int i = 0; i < twentyOne.length; i++) {
            twentyOne[i] = validImage(managedPath(i));
        }
        assertRejected(withImages(twentyOne));
    }

    @Test
    @DisplayName("images 受管路径不得重复")
    void imagesDuplicateManagedPathRejected() {
        assertRejected(withImages(validImage(managedPath(3)), validImage(managedPath(3))));

        assertEquals(2, normalize(withImages(validImage(managedPath(3)), validImage(managedPath(4)))).images().size());
    }

    @Test
    @DisplayName("images 路径形状：/uploads/YYYY/MM/32位小写hex.(jpg|png|webp)")
    void imagesManagedPathShape() {
        for (String extension : List.of("jpg", "png", "webp")) {
            DestinationDetailsValues values = normalize(withImages(validImage(managedPath(5, extension))));
            assertEquals(managedPath(5, extension), values.images().get(0).url());
        }

        for (String bad : List.of(
                "https://cdn.example.com/uploads/2024/01/" + "a".repeat(32) + ".jpg",
                "/uploads/2024/13/" + "a".repeat(32) + ".jpg",
                "/uploads/2024/00/" + "a".repeat(32) + ".jpg",
                "/uploads/2024/1/" + "a".repeat(32) + ".jpg",
                "/uploads/24/01/" + "a".repeat(32) + ".jpg",
                "/uploads/2024/01/" + "a".repeat(31) + ".jpg",
                "/uploads/2024/01/" + "a".repeat(33) + ".jpg",
                "/uploads/2024/01/" + "A".repeat(32) + ".jpg",
                "/uploads/2024/01/" + "a".repeat(32) + ".gif",
                "/uploads/2024/01/" + "a".repeat(32),
                "/uploads/2024/01/" + "a".repeat(32) + ".jpg?x=1",
                "/uploads/2024/01/../" + "a".repeat(32) + ".jpg",
                "/uploads/2024/01/" + "a".repeat(30) + "-b.jpg")) {
            assertRejected(withImages(validImage(bad)));
        }
    }

    @Test
    @DisplayName("images 必填署名：altText/author/license")
    void imagesAttributionRequired() {
        assertRejected(withImages(image(managedPath(1), null, "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "  ", "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", null, LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", " ", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", "张三", null, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", "张三", "", GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
    }

    @Test
    @DisplayName("images 文本长度：altText 200 / author 300 / license 100")
    void imagesTextLengthLimits() {
        assertEquals(200, normalize(withImages(
                image(managedPath(1), "a".repeat(200), "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)))
                .images().get(0).altText().length());
        assertRejected(withImages(
                image(managedPath(1), "a".repeat(201), "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));

        assertEquals(300, normalize(withImages(
                image(managedPath(1), "alt", "a".repeat(300), LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)))
                .images().get(0).author().length());
        assertRejected(withImages(
                image(managedPath(1), "alt", "a".repeat(301), LICENSE, GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));

        assertEquals(100, normalize(withImages(
                image(managedPath(1), "alt", "张三", "a".repeat(100), GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)))
                .images().get(0).license().length());
        assertRejected(withImages(
                image(managedPath(1), "alt", "张三", "a".repeat(101), GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)));
    }

    @Test
    @DisplayName("images sourceUrl/licenseUrl 必须为有效 URL")
    void imagesUrlsValidated() {
        assertRejected(withImages(image(managedPath(1), "alt", "张三", LICENSE, null, LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", "张三", LICENSE, "ftp://example.com/s", LICENSE_URL, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", "张三", LICENSE, GOOD_SOURCE, null, RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(1), "alt", "张三", LICENSE, GOOD_SOURCE, "https://u:p@example.com/x", RETRIEVED_AT)));
    }

    @Test
    @DisplayName("images retrievedAt 必填且为 ISO 时间")
    void imagesRetrievedAtRequired() {
        for (String bad : new String[]{null, "", "2024-01-02", "not-a-time"}) {
            assertRejected(withImages(image(managedPath(1), "alt", "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, bad)));
        }

        DestinationImageValues values = normalize(withImages(
                image(managedPath(1), "alt", "张三", LICENSE, GOOD_SOURCE, LICENSE_URL, "2024-01-01T19:04:05Z")))
                .images().get(0);
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), values.retrievedAt());
    }

    @Test
    @DisplayName("images 列表不得含 null 元素")
    void imagesNullElementRejected() {
        DestinationWriteRequest request = empty();
        List<DestinationImageRequest> list = new ArrayList<>();
        list.add(validImage());
        list.add(null);
        request.setImages(list);
        assertRejected(request);
    }

    @Test
    @DisplayName("images 字段文本 strip 且 CRLF 归一")
    void imagesTextNormalized() {
        DestinationImageValues values = normalize(withImages(
                image(managedPath(1), "  外滩\r\n夜景  ", " 张三 ", " CC BY 4.0 ", GOOD_SOURCE, LICENSE_URL, RETRIEVED_AT)))
                .images().get(0);
        assertEquals("外滩\n夜景", values.altText());
        assertEquals("张三", values.author());
        assertEquals("CC BY 4.0", values.license());
        assertEquals(managedPath(1), values.url());
        assertEquals(GOOD_SOURCE, values.sourceUrl());
        assertEquals(LICENSE_URL, values.licenseUrl());
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), values.retrievedAt());
    }

    @Test
    @DisplayName("规范化后 images 为不可变列表且与请求列表解耦")
    void imagesListIsImmutableCopy() {
        List<DestinationImageRequest> mutable = new ArrayList<>();
        mutable.add(validImage());
        DestinationWriteRequest request = empty();
        request.setImages(mutable);

        DestinationDetailsValues values = normalize(request);
        mutable.clear();
        assertEquals(1, values.images().size());
        assertThrows(UnsupportedOperationException.class, () -> values.images().add(null));
        assertThrows(UnsupportedOperationException.class, () -> values.images().clear());
    }

    // ---------------------------------------------------------------- 统一异常

    @Test
    @DisplayName("任何非法输入统一抛 ApiException(VALIDATION_FAILED)，消息不泄露输入")
    void unifiedValidationFailure() {
        Set<DestinationWriteRequest> invalids = Set.of(
                withImages(validImage("https://evil.example/leak.jpg")),
                withCoordinates("999.999999999", "999.999999999"),
                withImages(validImage(managedPath(1)), validImage(managedPath(1))));

        for (DestinationWriteRequest invalid : invalids) {
            ApiException exception = invalid(invalid);
            assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
            assertEquals("目的地资料填写不合法", exception.getMessage());
            assertFalse(exception.getMessage().contains("evil"));
            assertFalse(exception.getMessage().contains("999"));
            assertNull(exception.getCause());
        }
    }

    // ---------------------------------------------------------------- 全量

    @Test
    @DisplayName("完整合法请求整体规范化")
    void fullValidPayloadNormalizes() {
        DestinationWriteRequest request = withSourceAndVerification();
        request.setAddress("  北京市朝阳区" + "\r\n" + "  ");
        request.setLongitude("116.407526");
        request.setLatitude("39.904030");
        request.setRecommendedDurationMinutes(180);
        request.setRecommendedDays(2);
        request.setOpeningHours("09:00-17:00");
        request.setTicketInfo("免费");
        request.setOfficialUrl("https://official.example.com/visit");
        List<DestinationImageRequest> images = new ArrayList<>();
        images.add(validImage());
        images.add(validImage(managedPath(1, "webp")));
        request.setImages(images);

        DestinationDetailsValues values = normalize(request);

        assertEquals("北京市朝阳区", values.address());
        assertCoordinate(values.longitude(), "116.407526");
        assertCoordinate(values.latitude(), "39.904030");
        assertEquals(180, values.recommendedDurationMinutes());
        assertEquals(2, values.recommendedDays());
        assertEquals("09:00-17:00", values.openingHours());
        assertEquals("免费", values.ticketInfo());
        assertEquals("https://official.example.com/visit", values.officialUrl());
        assertEquals(GOOD_SOURCE, values.sourceUrl());
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), values.verifiedAt());
        assertEquals(2, values.images().size());
        assertEquals(managedPath(0), values.images().get(0).url());
        assertEquals(managedPath(1, "webp"), values.images().get(1).url());
    }
    @Test void imageSourceUrlsMustRespectDatabaseLength() {
        String oversized="https://example.com/"+"x".repeat(1000);
        assertRejected(withImages(image(managedPath(0),"照片","作者",LICENSE,oversized,LICENSE_URL,RETRIEVED_AT)));
        assertRejected(withImages(image(managedPath(0),"照片","作者",LICENSE,GOOD_SOURCE,oversized,RETRIEVED_AT)));
    }
    @Test void loneCarriageReturnIsNormalizedConsistently() {
        DestinationWriteRequest request=empty();request.setAddress("第一行\r第二行");
        assertEquals("第一行\n第二行",normalize(request).address());
    }
    @Test
    void extremeOffsetDateMustBeValidationFailure() {
        var request = empty();
        request.setVerifiedAt("+999999999-12-31T23:59:59-18:00");
        assertRejected(request);
    }
}
