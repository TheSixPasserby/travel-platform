package com.example.travelplatform.file;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 Task：图片上传与公开读取的真实 HTTP 验收测试（契约 §17）。
 *
 * <p>固定约束：
 * <ul>
 *   <li>真实 {@link SpringBootTest}（RANDOM_PORT）+ test profile，全部使用原始 HTTP + JsonNode + JdbcTemplate；</li>
 *   <li>只消费共享 {@link HttpTestSupport}，不 mock Service/Mapper，不依赖尚未实现的 DTO/Entity；</li>
 *   <li>上传成功必须 201 且 data 仅含 {@code url}（{@code /uploads/...}），不返回原文件名或磁盘路径；</li>
 *   <li>公开读取必须准确 Content-Type + {@code X-Content-Type-Options: nosniff}，且能被 ImageIO 实际解码；</li>
 *   <li>边界：精确 5 MiB 有效图片 201；超 5 MiB 或总 multipart 超 6 MiB 为 HTTP 413/41301；
 *       单边 &gt;10000 或总像素 &gt;2500 万为 40001；</li>
 *   <li>失败请求不得新增文件、不得创建任何业务记录；成功上传只新增服务端生成的一个文件，已有文件不得被删除；</li>
 *   <li>所有图片夹具都位于本类私有临时父目录内的 root / outsideFixture，AfterAll 只删除该父目录；
 *       {@code Files.walk} 不跟随符号链接，因此不会误删受管根之外的内容；</li>
 *   <li>断言信息只包含状态码/业务码/URL，永不输出 token、密码或完整登录响应。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImageApiTest {

    private static final String NAMESPACE = "m2i";
    private static final String UPLOAD_PATH = "/api/v1/files/images";
    private static final String PUBLIC_PREFIX = "/uploads/";
    private static final String UPLOAD_DIR_PROPERTY = "travel.storage.upload-dir";
    private static final String NOSNIFF = "nosniff";
    private static final String CHINESE_FILENAME = "旅行图片.png";
    private static final String WEBP_FIXTURE = "/fixtures/images/green-8x8.webp";
    private static final int MEBIBYTE = 1024 * 1024;
    private static final int MAX_FILE_BYTES = 5 * MEBIBYTE;
    private static final int MAX_REQUEST_BYTES = 6 * MEBIBYTE;
    private static final int IMAGE_SIDE = 8;

    /** 本类私有临时父目录：root 为受管上传根，outsideFixture 为受管根之外的真实文件。 */
    private static final Path PRIVATE_PARENT;
    private static final Path MANAGED_ROOT;
    private static final Path OUTSIDE_PNG;
    private static final byte[] OUTSIDE_PNG_BYTES;

    static {
        try {
            PRIVATE_PARENT = Files.createTempDirectory("travel-image-api-").toAbsolutePath().normalize();
            MANAGED_ROOT = Files.createDirectories(PRIVATE_PARENT.resolve("root"));
            Path outsideDirectory = Files.createDirectories(PRIVATE_PARENT.resolve("outsideFixture"));
            OUTSIDE_PNG_BYTES = pngImage();
            OUTSIDE_PNG = Files.write(outsideDirectory.resolve("outside.png"), OUTSIDE_PNG_BYTES);
            // 受管根既可能是 <root>，也可能是 <root>/uploads；两种映射都由镜像夹具覆盖，
            // 测试不把磁盘布局写死，只验证 URL 语义边界。
            Files.createDirectories(MANAGED_ROOT.resolve("uploads"));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    private HttpTestSupport helper;

    @DynamicPropertySource
    static void registerPrivateUploadRoot(DynamicPropertyRegistry registry) {
        registry.add(UPLOAD_DIR_PROPERTY, MANAGED_ROOT::toString);
    }

    @BeforeEach
    void createPrivateFixtures() {
        helper = new HttpTestSupport(dataSource, port, NAMESPACE);
    }

    @AfterEach
    void cleanupOwnFixtures() {
        HttpTestSupport current = helper;
        helper = null;
        if (current == null) {
            return;
        }
        try {
            current.cleanupDestinations();
        } finally {
            current.cleanupAccounts();
        }
    }

    @AfterAll
    static void removePrivateTemporaryParent() {
        Path systemTemporary = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        assertThat(PRIVATE_PARENT.normalize().startsWith(systemTemporary))
                .as("只允许清理本测试在系统临时目录下创建的上传父目录").isTrue();
        assertThat(PRIVATE_PARENT.getFileName().toString())
                .as("只允许清理本测试命名的临时目录").startsWith("travel-image-api-");
        try (Stream<Path> paths = Files.walk(PRIVATE_PARENT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException("清理测试私有上传目录失败", exception);
        }
    }

    // ---------------------------------------------------------------------
    // 1. PNG 上传 + 匿名安全读取
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("登录用户上传 PNG：201 仅返回受管 URL，匿名可安全读取并真实解码")
    void authenticatedUserUploadsPngAndReadsItSafely() {
        HttpTestSupport.Account user = helper.account(false);
        Map<String, Long> rowsBefore = businessRowCounts();

        String originalName = helper.named(CHINESE_FILENAME);
        JsonNode data = uploadCreated(user.token(), filePart(originalName, "image/png", pngImage()));
        String url = data.path("url").asText();
        assertThat(url).as("受管访问路径").matches("/uploads/[A-Za-z0-9._/-]+");
        assertThat(url).as("不得包含请求中的原文件名或测试夹具前缀")
                .doesNotContain("旅行图片")
                .doesNotContain(URLEncoder.encode(CHINESE_FILENAME, StandardCharsets.UTF_8))
                .doesNotContain(helper.prefix())
                .doesNotContain("//")
                .doesNotContain("..");
        assertThat(data.toString()).as("不得返回磁盘路径")
                .doesNotContain(MANAGED_ROOT.toString())
                .doesNotContain(PRIVATE_PARENT.toString());

        String secondUrl = uploadUrl(user.token(), "再次上传.png", "image/png", pngImage());
        assertThat(secondUrl).as("服务端文件名必须随机").isNotEqualTo(url);

        HttpTestSupport.Result served = getPublic(url);
        assertServedImage(served, "image/png");
        assertDecodable(served.bytes(), IMAGE_SIDE, IMAGE_SIDE);
        assertThat(businessRowCounts()).as("上传成功不得创建业务记录").isEqualTo(rowsBefore);
        JsonNode profile = HttpTestSupport.success(helper.json("PATCH", "/api/v1/users/me", user.token(),
                Map.of("avatarUrl", url)), 200);
        assertThat(profile.path("avatarUrl").asText()).as("上传路径能被既有资料校验使用").isEqualTo(url);
    }

    // ---------------------------------------------------------------------
    // 2. 三种允许格式的媒体类型与解码
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("JPEG/PNG/WebP 上传成功，公开读取媒体类型准确且内容可解码")
    void jpegPngAndWebpUploadsServeMatchingMediaTypesAndDecode() {
        HttpTestSupport.Account user = helper.account(false);
        Map<String, Long> rowsBefore = businessRowCounts();

        HttpTestSupport.Result png = getPublic(uploadUrl(user.token(), "照片.png", "image/png", pngImage()));
        assertServedImage(png, "image/png");
        assertDecodable(png.bytes(), IMAGE_SIDE, IMAGE_SIDE);

        HttpTestSupport.Result jpeg = getPublic(uploadUrl(user.token(), "照片.jpg", "image/jpeg", jpegImage()));
        assertServedImage(jpeg, "image/jpeg");
        assertDecodable(jpeg.bytes(), IMAGE_SIDE, IMAGE_SIDE);

        byte[] webp = webpFixture();
        HttpTestSupport.Result servedWebp = getPublic(uploadUrl(user.token(), "照片.webp", "image/webp", webp));
        assertServedImage(servedWebp, "image/webp");
        assertThat(servedWebp.bytes()).as("WebP 字节应原样读回").isEqualTo(webp);
        assertDecodable(servedWebp.bytes(), IMAGE_SIDE, IMAGE_SIDE);

        assertThat(businessRowCounts()).as("上传成功不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 3. 匿名上传
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("匿名上传返回 401/40101，且不新增文件与业务记录")
    void anonymousUploadIsRejectedWithoutWritingAnything() {
        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();

        HttpTestSupport.Result response =
                multipartUpload(null, filePart("匿名.png", "image/png", pngImage()));
        HttpTestSupport.error(response, 401, 40101);

        assertThat(managedFiles()).as("匿名上传不得新增文件").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("匿名上传不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 4. 空文件 / 缺 file / 多 file
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("空文件、缺 file 字段、多 file 均返回 400/40001 且不落盘")
    void emptyMissingAndMultipleFilesAreRejectedAsBadRequest() {
        HttpTestSupport.Account user = helper.account(false);
        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();

        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("空文件.png", "image/png", new byte[0])), 400, 40001);
        HttpTestSupport.error(multipartUpload(user.token(),
                new MultipartPart("image", "错误字段.png", "image/png", pngImage())), 400, 40001);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("第一个.png", "image/png", pngImage()),
                filePart("第二个.png", "image/png", pngImage())), 400, 40001);

        assertThat(managedFiles()).as("被拒绝的上传不得新增文件").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("被拒绝的上传不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 5. SVG / 错扩展名 / MIME 不一致 / 损坏字节
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("SVG、错误扩展名、声明 MIME 与实际不符、损坏字节均返回 415/41501")
    void unsupportedOrMismatchedImagesAreRejectedAsUnsupportedMedia() {
        HttpTestSupport.Account user = helper.account(false);
        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();

        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"8\" height=\"8\">"
                + "<rect width=\"8\" height=\"8\" fill=\"#145C45\"/></svg>").getBytes(StandardCharsets.UTF_8);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("矢量.svg", "image/svg+xml", svg)), 415, 41501);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("伪装.png", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8))), 415, 41501);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("错误扩展.txt", "image/png", pngImage())), 415, 41501);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("声明不符.png", "image/jpeg", pngImage())), 415, 41501);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("实际格式不符.png", "image/png", jpegImage())), 415, 41501);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("损坏.png", "image/png", UUID.randomUUID().toString()
                        .getBytes(StandardCharsets.UTF_8))), 415, 41501);

        assertThat(managedFiles()).as("被拒绝的上传不得新增文件").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("被拒绝的上传不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 6. 5 MiB / 6 MiB 大小边界
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("精确 5 MiB 有效 PNG 为 201，5 MiB+1 与总 multipart 超 6 MiB 为 413")
    void exactFiveMebibyteImageIsAcceptedButOversizedRequestsAreRejected() {
        HttpTestSupport.Account user = helper.account(false);

        // 追加一个合法 tEXt 块精确命中 5 MiB 边界：文件仍是结构完全合法的 PNG。
        byte[] exact = pngOfSize(MAX_FILE_BYTES);
        assertThat(exact).hasSize(MAX_FILE_BYTES);

        String maxSizeUrl = uploadUrl(user.token(), "五兆边界.png", "image/png", exact);
        HttpTestSupport.Result served = getPublic(maxSizeUrl);
        assertServedImage(served, "image/png");
        assertThat(served.bytes()).as("5 MiB 文件应完整保存并可读回").hasSize(MAX_FILE_BYTES);
        assertDecodable(served.bytes(), IMAGE_SIDE, IMAGE_SIDE);

        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();

        byte[] tooLarge = pngOfSize(MAX_FILE_BYTES + 1);
        assertRequestPayloadTooLarge(multipartUpload(user.token(),
                filePart("超出一字节.png", "image/png", tooLarge)));

        // 两个文件各自小于 5 MiB，但 multipart 总量超过 6 MiB：容器先拒绝为 413，而不是多文件 400。
        byte[] each = pngOfSize(3_400_000);
        assertThat(2L * each.length).as("两个文件总字节数必须超过 6 MiB 请求上限")
                .isGreaterThan(MAX_REQUEST_BYTES);
        assertRequestPayloadTooLarge(multipartUpload(user.token(),
                filePart("合并一.png", "image/png", each),
                filePart("合并二.png", "image/png", each)));

        assertThat(managedFiles()).as("超限请求不得新增文件").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("超限请求不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 7. 像素尺寸边界（仅改 IHDR，读头即拒绝，不分配巨图）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("单边超 10000 或总像素超 2500 万的 PNG 读取头部后返回 400/40001")
    void oversizedPixelDimensionsAreRejectedFromHeaderOnly() {
        HttpTestSupport.Account user = helper.account(false);
        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();

        // 只改写 IHDR 声明的宽高并重算 CRC：IDAT 并不匹配，若实现先完整解码会得到 415 而非 40001。
        byte[] wider = pngWithDeclaredDimensions(10_001, IMAGE_SIDE);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("超宽.png", "image/png", wider)), 400, 40001);

        byte[] huge = pngWithDeclaredDimensions(6_000, 5_000);
        assertThat(6_000L * 5_000L).as("夹具总像素必须超过 2500 万").isGreaterThan(25_000_000L);
        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("超像素.png", "image/png", huge)), 400, 40001);

        assertThat(managedFiles()).as("超尺寸请求不得新增文件").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("超尺寸请求不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // 8. 公开读取的受管根边界
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("文本文件、目录伪装、外指符号链接、缺失文件 GET 均 404/40401，穿越不读取外部文件")
    void managedImageReadRejectsDisguisesMissingFilesAndEscapingLinks() {
        String textName = helper.named("read-text.txt");
        String directoryName = helper.named("read-directory.png");
        String escapeName = helper.named("read-escape.png");
        String missingName = helper.named("read-missing.png");
        mirrorFile(textName, "不是图片".getBytes(StandardCharsets.UTF_8));
        mirrorDirectory(directoryName);
        mirrorSymlink(escapeName, OUTSIDE_PNG);

        for (String name : List.of(textName, directoryName, escapeName, missingName)) {
            HttpTestSupport.error(getPublic(PUBLIC_PREFIX + name), 404, 40401);
        }

        for (String path : List.of(
                "/uploads/%2e%2e/outsideFixture/outside.png",
                "/uploads/..%2F..%2FoutsideFixture%2Foutside.png",
                "/uploads/%2e%2e%2FoutsideFixture%2Foutside.png")) {
            HttpTestSupport.Result response = getPublic(path);
            // 编码穿越可能先被容器拒绝为 400；若到达应用则必须是 404。两种都不得返回外部文件内容。
            assertThat(response.status()).as("穿越路径必须被拒绝: " + path + ", type="
                    + response.header("Content-Type") + ", bytes=" + response.bytes().length).isIn(400, 404);
            assertThat(response.bytes()).as("不得读取受管根之外的文件").isNotEqualTo(OUTSIDE_PNG_BYTES);
        }
    }

    // ---------------------------------------------------------------------
    // 9. 失败与成功请求对已有文件/业务表的影响
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("失败请求不改变已有文件与业务计数，成功上传只新增一个服务端文件且保留旧文件")
    void failedAndSuccessfulUploadsKeepExistingFilesAndBusinessRowsIntact() throws Exception {
        HttpTestSupport.Account user = helper.account(false);
        String existingImageName = helper.named("existing.png");
        String existingTextName = helper.named("existing.txt");
        byte[] existingImageBytes = pngImage();
        mirrorFile(existingImageName, existingImageBytes);
        mirrorFile(existingTextName, "合法已有文件".getBytes(StandardCharsets.UTF_8));

        Map<String, Long> rowsBefore = businessRowCounts();
        Set<String> filesBefore = managedFiles();
        assertThat(filesBefore).contains(
                existingImageName, "uploads/" + existingImageName,
                existingTextName, "uploads/" + existingTextName);

        HttpTestSupport.error(multipartUpload(user.token(),
                filePart("损坏请求.png", "image/png", "broken".getBytes(StandardCharsets.UTF_8))), 415, 41501);
        assertRequestPayloadTooLarge(multipartUpload(user.token(),
                filePart("超限请求.png", "image/png", pngOfSize(MAX_FILE_BYTES + 1))));

        assertThat(managedFiles()).as("失败请求前后已存在文件集合不变").isEqualTo(filesBefore);
        assertThat(businessRowCounts()).as("失败请求前后业务表计数不变").isEqualTo(rowsBefore);

        byte[] uploadedBytes = pngImage();
        String uploadedUrl = uploadUrl(user.token(), "新增图片.png", "image/png", uploadedBytes);

        Set<String> filesAfter = managedFiles();
        Set<String> added = new TreeSet<>(filesAfter);
        added.removeAll(filesBefore);
        // 成功上传只允许新增服务端生成的一个已发布文件；若临时文件残留会被计为额外增长。
        String relative = uploadedUrl.substring(PUBLIC_PREFIX.length());
        List<String> published = added.stream()
                .filter(path -> path.equals(relative) || path.equals("uploads/" + relative))
                .toList();
        assertThat(added).as("成功上传只新增一个服务端生成文件（返回 URL 对应的那一个）")
                .containsExactlyElementsOf(published);
        assertThat(published).as("新增文件必须位于本类注入的受管根内且与返回 URL 一致").hasSize(1);
        assertThat(published.get(0)).as("磁盘名不得沿用原文件名").doesNotContain("新增图片");
        assertThat(Files.readAllBytes(MANAGED_ROOT.resolve(published.get(0))))
                .as("落盘内容应与上传字节一致").isEqualTo(uploadedBytes);

        assertThat(filesAfter).as("已有文件不得被删除").containsAll(filesBefore);
        assertThat(Files.readAllBytes(MANAGED_ROOT.resolve(existingImageName)))
                .as("已有合法图片内容不得被改写").isEqualTo(existingImageBytes);
        assertServedImage(getPublic(uploadedUrl), "image/png");
        assertThat(businessRowCounts()).as("成功上传不得创建业务记录").isEqualTo(rowsBefore);
    }

    // ---------------------------------------------------------------------
    // multipart / HTTP 夹具
    // ---------------------------------------------------------------------

    private record MultipartPart(String fieldName, String fileName, String contentType, byte[] bytes) {
    }

    private static MultipartPart filePart(String fileName, String contentType, byte[] bytes) {
        return new MultipartPart("file", fileName, contentType, bytes);
    }

    private HttpTestSupport.Result multipartUpload(String token, MultipartPart... parts) {
        String boundary = "----ImageApiTest" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (MultipartPart part : parts) {
            body.writeBytes(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(("Content-Disposition: form-data; name=\"" + part.fieldName()
                    + "\"; filename=\"" + part.fileName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(("Content-Type: " + part.contentType() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.writeBytes(part.bytes());
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return helper.requestBytes("POST", UPLOAD_PATH, token, body.toByteArray(),
                Map.of("Content-Type", "multipart/form-data; boundary=" + boundary));
    }

    private JsonNode uploadCreated(String token, MultipartPart... parts) {
        JsonNode data = HttpTestSupport.success(multipartUpload(token, parts), 201);
        assertThat(data.isObject()).as("上传成功 data 必须是 JSON 对象").isTrue();
        assertThat(data.size()).as("上传成功 data 仅包含 url").isEqualTo(1);
        assertThat(data.hasNonNull("url")).as("上传成功 data 必须包含 url").isTrue();
        return data;
    }

    private String uploadUrl(String token, String fileName, String contentType, byte[] bytes) {
        String url = uploadCreated(token, filePart(fileName, contentType, bytes)).path("url").asText();
        assertThat(url).as("受管访问路径").matches("/uploads/[A-Za-z0-9._/-]+");
        return url;
    }

    private HttpTestSupport.Result getPublic(String path) {
        return helper.request("GET", path, null, null, Map.of());
    }

    private static void assertServedImage(HttpTestSupport.Result response, String mediaType) {
        assertThat(response.status()).as("匿名读取已上传图片").isEqualTo(200);
        assertThat(response.header("Content-Type")).as("准确 Content-Type").isEqualToIgnoringCase(mediaType);
        assertThat(response.header("X-Content-Type-Options")).as("禁止 MIME 嗅探")
                .isEqualToIgnoringCase(NOSNIFF);
    }

    private static void assertRequestPayloadTooLarge(HttpTestSupport.Result response) {
        HttpTestSupport.error(response, 413, 41301);
    }

    // ---------------------------------------------------------------------
    // 图片夹具
    // ---------------------------------------------------------------------

    private static BufferedImage solidImage(int imageType) {
        BufferedImage image = new BufferedImage(IMAGE_SIDE, IMAGE_SIDE, imageType);
        for (int x = 0; x < IMAGE_SIDE; x++) {
            for (int y = 0; y < IMAGE_SIDE; y++) {
                image.setRGB(x, y, 0xFF145C45);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertThat(ImageIO.write(image, format, out)).as("JDK ImageIO 应能生成 " + format + " 夹具").isTrue();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError("无法生成 " + format + " 夹具");
        }
    }

    private static byte[] pngImage() {
        return encode(solidImage(BufferedImage.TYPE_INT_ARGB), "png");
    }

    private static byte[] jpegImage() {
        return encode(solidImage(BufferedImage.TYPE_INT_RGB), "jpg");
    }

    private static byte[] webpFixture() {
        try (InputStream stream = ImageApiTest.class.getResourceAsStream(WEBP_FIXTURE)) {
            assertThat(stream).as("classpath WebP 夹具必须存在").isNotNull();
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new AssertionError("无法读取 WebP 夹具");
        }
    }

    /**
     * 生成精确 size 字节的结构完全合法的 PNG：在 IEND 前插入一个 tEXt 块，
     * 这样 5 MiB 边界测试面对的是真实可解码图片，而不是尾部垃圾字节。
     */
    private static byte[] pngOfSize(int size) {
        byte[] png = pngImage();
        assertThat(size).as("目标大小必须大于基础 PNG 且留出最小块空间")
                .isGreaterThan(png.length + 12);
        int textLength = size - png.length - 12;
        byte[] padded = new byte[size];
        int iendStart = png.length - 12; // IEND: 长度4 + 类型4 + CRC4
        System.arraycopy(png, 0, padded, 0, iendStart);
        writeInt(padded, iendStart, textLength);
        byte[] textType = "tEXt".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(textType, 0, padded, iendStart + 4, 4);
        byte[] payload = new byte[textLength];
        payload[0] = 'k';
        payload[1] = 0;
        System.arraycopy(payload, 0, padded, iendStart + 8, textLength);
        CRC32 crc = new CRC32();
        crc.update(padded, iendStart + 4, 4 + textLength);
        writeInt(padded, iendStart + 8 + textLength, (int) crc.getValue());
        System.arraycopy(png, iendStart, padded, iendStart + 12 + textLength, 12);
        return padded;
    }

    /**
     * 取合法 8x8 PNG，只改写 IHDR 声明的宽高并重算 CRC。
     * 这样实现只需读取图片头部即可按尺寸拒绝，无需分配巨图；若先完整解码则会解码失败。
     */
    private static byte[] pngWithDeclaredDimensions(int width, int height) {
        byte[] png = pngImage();
        int position = 8;
        while (position + 8 <= png.length) {
            int length = readInt(png, position);
            String type = new String(png, position + 4, 4, StandardCharsets.ISO_8859_1);
            if ("IHDR".equals(type)) {
                assertThat(length).as("IHDR 长度").isEqualTo(13);
                writeInt(png, position + 8, width);
                writeInt(png, position + 12, height);
                CRC32 crc = new CRC32();
                crc.update(png, position + 4, 4 + length);
                writeInt(png, position + 8 + length, (int) crc.getValue());
                return png;
            }
            position += 12 + length;
        }
        throw new AssertionError("夹具 PNG 缺少 IHDR");
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    private static void assertDecodable(byte[] bytes, int expectedWidth, int expectedHeight) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertThat(image).as("公开读取的图片应能被 ImageIO 实际解码").isNotNull();
            assertThat(image.getWidth()).as("解码宽度").isEqualTo(expectedWidth);
            assertThat(image.getHeight()).as("解码高度").isEqualTo(expectedHeight);
        } catch (IOException exception) {
            throw new AssertionError("公开读取的图片解码失败");
        }
    }

    // ---------------------------------------------------------------------
    // 受管根内夹具与快照
    // ---------------------------------------------------------------------

    private static void mirrorFile(String name, byte[] bytes) {
        try {
            Files.write(MANAGED_ROOT.resolve(name), bytes);
            Files.write(Files.createDirectories(MANAGED_ROOT.resolve("uploads")).resolve(name), bytes);
        } catch (IOException exception) {
            throw new AssertionError("无法准备受管图片夹具");
        }
    }

    private static void mirrorDirectory(String name) {
        try {
            Files.createDirectories(MANAGED_ROOT.resolve(name));
            Files.createDirectories(MANAGED_ROOT.resolve("uploads").resolve(name));
        } catch (IOException exception) {
            throw new AssertionError("无法准备目录伪装夹具");
        }
    }

    private static void mirrorSymlink(String name, Path target) {
        try {
            Files.createSymbolicLink(MANAGED_ROOT.resolve(name), target);
            Files.createSymbolicLink(MANAGED_ROOT.resolve("uploads").resolve(name), target);
        } catch (IOException exception) {
            throw new AssertionError("无法准备符号链接夹具");
        }
    }

    /** 只统计本类受管根内的普通文件；不跟随符号链接，避免把根外目标算进来。 */
    private static Set<String> managedFiles() {
        try (Stream<Path> paths = Files.walk(MANAGED_ROOT)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> MANAGED_ROOT.relativize(path).toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException exception) {
            throw new AssertionError("无法列举受管上传根");
        }
    }

    /** 当前测试库全部基础表的行数快照：任何上传都不得创建业务记录。 */
    private Map<String, Long> businessRowCounts() {
        helper.assertIsolatedDatabase();
        List<String> tables = helper.jdbc().queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "ORDER BY table_name", String.class);
        assertThat(tables).as("测试库必须已有业务表").isNotEmpty();
        Map<String, Long> counts = new TreeMap<>();
        for (String table : tables) {
            assertThat(table).as("表名必须可安全用于计数").matches("[A-Za-z0-9_]+");
            Long count = helper.jdbc().queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class);
            counts.put(table, count);
        }
        return counts;
    }
}
