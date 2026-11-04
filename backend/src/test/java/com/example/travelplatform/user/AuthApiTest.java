package com.example.travelplatform.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * M1 Task 2：真实 HTTP 认证与资料验收测试。
 *
 * <p>固定约束：
 * <ul>
 *   <li>真实 {@link SpringBootTest}（RANDOM_PORT）+ test profile + 真实 MySQL；</li>
 *   <li>HTTP 只用 JDK {@link HttpClient}，JSON 用 Jackson，数据校验用 {@link JdbcTemplate}；</li>
 *   <li>不 mock Service/Mapper，不依赖待实现 DTO 类型，全部按 JSON 契约断言；</li>
 *   <li>清理前必须确认 {@code SELECT DATABASE()} 等于独立 {@code *_test} 库且不等于 {@code DB_NAME}；</li>
 *   <li>每个测试用 {@code m1t + 16 位十六进制} 随机前缀账号，只删除本前缀夹具，不 TRUNCATE、不删库。</li>
 * </ul>
 *
 * <p>断言失败信息不打印有效 token、密码或完整响应体，避免把凭据写进测试日志。
 * 集成测试由主代理注入 DB 环境后运行；未注入环境时本类不应被执行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthApiTest {

    private static final Logger log = LoggerFactory.getLogger(AuthApiTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    /** 测试账号前缀：m1t + 16 位十六进制（无横线 UUID 片段）。 */
    private static final String TEST_PREFIX_REGEX = "m1t[0-9a-f]{16}";
    /** 夹具密码：不 trim、非空白、8 个以上 ASCII 码点、72 字节以内。 */
    private static final String PASSWORD = "M1-Auth-2026-Secret";

    /**
     * createdAt 必须是带 +08:00 的 ISO 8601 字符串。
     * 小数秒按 Jackson 默认 ISO_OFFSET_DATE_TIME 输出，DATETIME(3) 的毫秒会省略尾零
     * （例如 .120 输出为 .12、.000 输出为无小数），因此这里允许 0–9 位小数；
     * 毫秒精度由下面的「响应时间 vs 数据库时间」比对单独验证。
     */
    private static final String SHANGHAI_TIMESTAMP_REGEX =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?\\+08:00";
    private static final ZoneOffset SHANGHAI_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter DATABASE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

    /** 当前用户安全投影：恰好这 8 个字段，顺序不作为契约。 */
    private static final Set<String> SAFE_USER_FIELDS = Set.of(
            "id", "username", "nickname", "avatarUrl", "bio", "role", "status", "createdAt");

    /** 大于 2^53 - 1 的 ID，用于验证 JSON 中始终是字符串。 */
    private static final long LARGE_ID_FLOOR = 9_007_199_254_740_993L;
    private static final int CONCURRENT_REGISTRATIONS = 6;

    private static final String UPLOAD_ROOT_PROPERTY = "travel.storage.upload-dir";
    private static final String VALID_AVATAR_PATH = "/uploads/valid-avatar.png";
    private static final String INVALID_AVATAR_PATH = "/uploads/invalid-avatar.txt";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String DENIED_ORIGIN = "https://evil.example.com";

    /**
     * 测试私有上传根：类初始化时创建一次，DynamicPropertySource 注入，AfterAll 只清理本目录。
     * 使用 final 字段，保证同一 Context 复用期间（以及任何重载）目录不会被提前移除。
     */
    private static final Path UPLOAD_ROOT = createPrivateUploadRoot();

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String baseUrl;
    private String testPrefix;
    private boolean databaseIsolationVerified;
    private final AtomicInteger accountSequence = new AtomicInteger();

    // ---------------------------------------------------------------------
    // 测试私有上传根目录（上传路径校验用，不触碰真实 uploads/）
    // ---------------------------------------------------------------------

    @DynamicPropertySource
    static void registerManagedUploadRoot(DynamicPropertyRegistry registry) {
        registry.add(UPLOAD_ROOT_PROPERTY, UPLOAD_ROOT::toString);
    }

    private static Path createPrivateUploadRoot() {
        try {
            Path root = Files.createTempDirectory("travel-auth-upload-").toAbsolutePath().normalize();
            byte[] png = validPngBytes();
            // 受管根既可能是 <root>，也可能是 <root>/uploads：两种映射都放同一份夹具，
            // 让测试只验证「路径是否指向受管根内的真实图片」，不把磁盘布局约定写死。
            Files.write(root.resolve("valid-avatar.png"), png);
            Files.write(root.resolve("invalid-avatar.txt"), "not an image".getBytes(StandardCharsets.UTF_8));
            Path uploadsSegment = Files.createDirectories(root.resolve("uploads"));
            Files.write(uploadsSegment.resolve("valid-avatar.png"), png);
            Files.write(uploadsSegment.resolve("invalid-avatar.txt"), "not an image".getBytes(StandardCharsets.UTF_8));
            return root;
        } catch (IOException exception) {
            throw new UncheckedIOException("无法创建测试私有上传目录", exception);
        }
    }

    private static byte[] validPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, 0xFF2E7D5B);
            }
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return buffer.toByteArray();
    }

    // ---------------------------------------------------------------------
    // 生命周期：隔离校验 -> 测试 -> 仅清理本前缀夹具 / 本测试上传目录
    // ---------------------------------------------------------------------

    @BeforeEach
    void prepareIsolatedTestDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        baseUrl = "http://127.0.0.1:" + port + "/api/v1";
        requireIsolatedTestDatabase();
        databaseIsolationVerified = true;
        testPrefix = newTestPrefix();
        accountSequence.set(0);
    }

    @AfterEach
    void cleanupOnlyOwnFixtures() {
        try {
            if (!databaseIsolationVerified) {
                log.warn("未确认数据库隔离，跳过夹具清理，不删除任何数据");
                return;
            }
            if (!isActiveDatabaseIsolated()) {
                throw new AssertionError("清理前复核失败：当前连接的数据库不是独立测试库，拒绝清理");
            }
            deleteOwnFixtures();
        } finally {
            databaseIsolationVerified = false;
        }
    }

    @AfterAll
    void removePrivateUploadRoot() {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        assertThat(UPLOAD_ROOT.normalize().startsWith(tempRoot))
                .as("只允许清理本测试在系统临时目录下创建的上传根")
                .isTrue();
        try (Stream<Path> paths = Files.walk(UPLOAD_ROOT)) {
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
    // 环境隔离校验
    // ---------------------------------------------------------------------

    /**
     * 清理前隔离校验：必填两个库名、测试库名以 _test 结尾、两者不同、
     * 且当前连接的实际数据库等于 DB_TEST_NAME。失败信息不回显真实库名。
     */
    private void requireIsolatedTestDatabase() {
        String testDatabase = requiredEnvironment("DB_TEST_NAME");
        String developmentDatabase = requiredEnvironment("DB_NAME");
        assertThat(testDatabase.endsWith("_test")).as("DB_TEST_NAME 必须以 _test 结尾").isTrue();
        assertThat(testDatabase.equalsIgnoreCase(developmentDatabase)).as("DB_TEST_NAME 不得与 DB_NAME 相同").isFalse();
        String actualDatabase = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(testDatabase.equals(actualDatabase))
                .as("SELECT DATABASE() 必须是 DB_TEST_NAME 指定的独立测试库").isTrue();
    }

    private boolean isActiveDatabaseIsolated() {
        String testDatabase = System.getenv("DB_TEST_NAME");
        String developmentDatabase = System.getenv("DB_NAME");
        if (testDatabase == null || testDatabase.isBlank() || !testDatabase.endsWith("_test")
                || testDatabase.equalsIgnoreCase(developmentDatabase)) {
            return false;
        }
        try {
            String actualDatabase = jdbc.queryForObject("SELECT DATABASE()", String.class);
            return testDatabase.equals(actualDatabase);
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("缺少必需环境变量 " + name
                    + "：集成测试必须由启动器同时注入 DB_NAME 与 DB_TEST_NAME（测试不读取 .env）");
        }
        return value;
    }

    private static String newTestPrefix() {
        String prefix = "m1t" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        assertThat(prefix).as("测试账号前缀格式").matches(TEST_PREFIX_REGEX);
        return prefix;
    }

    private String nextUsername() {
        String username = testPrefix + "_u" + accountSequence.incrementAndGet();
        assertThat(username).as("夹具账号必须是合法用户名").matches("^[a-z0-9_]{3,50}$");
        return username;
    }

    private int countUsers(String username) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count == null ? -1 : count;
    }

    private void deleteOwnFixtures() {
        assertThat(testPrefix).matches(TEST_PREFIX_REGEX);
        int prefixLength = testPrefix.length();
        int deleted = jdbc.update("DELETE FROM users WHERE LEFT(username, ?) = ?", prefixLength, testPrefix);
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE LEFT(username, ?) = ?", Integer.class, prefixLength, testPrefix);
        assertThat(remaining).as("清理后本测试前缀的用户必须为 0").isZero();
        log.debug("已清理本测试前缀夹具用户 {} 条（前缀 {}）", deleted, testPrefix);
    }

    // ---------------------------------------------------------------------
    // HTTP 客户端与响应断言（失败信息不回显 token / 密码 / 完整响应体）
    // ---------------------------------------------------------------------

    private record ApiResult(int status, String rawBody, HttpHeaders headers) {

        JsonNode json() {
            try {
                return MAPPER.readTree(rawBody == null || rawBody.isBlank() ? "null" : rawBody);
            } catch (JsonProcessingException exception) {
                throw new AssertionError("响应体不是合法 JSON（HTTP " + status
                        + "，长度 " + (rawBody == null ? 0 : rawBody.length()) + "）");
            }
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }

        @Override
        public String toString() {
            // 不输出响应体：成功响应可能含 token/用户资料，避免进入失败日志。
            return "ApiResult[status=" + status + ", bodyLength=" + (rawBody == null ? 0 : rawBody.length()) + "]";
        }
    }

    private ApiResult get(String path) {
        return request("GET", path, null, null, Map.of());
    }

    private ApiResult getWithToken(String path, String token) {
        return request("GET", path, token, null, Map.of());
    }

    private ApiResult post(String path, String json) {
        return request("POST", path, null, json, Map.of());
    }

    private ApiResult postWithToken(String path, String token, JsonNode body) {
        return request("POST", path, token, body == null ? null : write(body), Map.of());
    }

    private ApiResult patchProfile(String token, java.util.function.Consumer<ObjectNode> customization) {
        ObjectNode body = object();
        customization.accept(body);
        return request("PATCH", "/users/me", token, write(body), Map.of());
    }

    private ApiResult request(String method, String path, String token, String json, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(REQUEST_TIMEOUT);
        if (token != null) {
            builder.header("satoken", token);
        }
        headers.forEach(builder::header);
        if (json != null) {
            builder.header("Content-Type", "application/json; charset=utf-8");
        }
        HttpRequest.BodyPublisher bodyPublisher = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        builder.method(method, bodyPublisher);
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ApiResult(response.statusCode(), response.body(), response.headers());
        } catch (IOException exception) {
            throw new IllegalStateException("HTTP 请求失败: " + method + " " + stripQuery(path), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 请求被中断: " + method + " " + stripQuery(path));
        }
    }

    private static String stripQuery(String path) {
        int queryIndex = path.indexOf('?');
        return queryIndex < 0 ? path : path.substring(0, queryIndex);
    }

    private ApiResult register(String username, String password) {
        return register(username, password, null);
    }

    private ApiResult register(String username, String password, String nickname) {
        ObjectNode body = object().put("username", username).put("password", password);
        if (nickname != null) {
            body.put("nickname", nickname);
        }
        return post("/auth/register", write(body));
    }

    private ApiResult login(String username, String password) {
        return post("/auth/login", write(object().put("username", username).put("password", password)));
    }

    private static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("构造测试 JSON 失败");
        }
    }

    private static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    private static void assertStatus(ApiResult result, int expectedStatus) {
        assertThat(result.status()).as("HTTP 状态不符合契约").isEqualTo(expectedStatus);
    }

    private static void assertSuccess(ApiResult result, int expectedHttpStatus) {
        assertStatus(result, expectedHttpStatus);
        JsonNode root = result.json();
        assertThat(root.path("code").asInt(Integer.MIN_VALUE)).as("成功响应 code 必须为 0").isZero();
        assertThat(root.path("message").asText(null)).as("成功响应 message 必须为 success").isEqualTo("success");
        assertThat(root.has("data")).as("成功响应必须保留 data 字段").isTrue();
    }

    private static void assertBusinessError(ApiResult result, int expectedHttpStatus, int expectedCode) {
        assertStatus(result, expectedHttpStatus);
        JsonNode root = result.json();
        assertThat(root.path("code").asInt(Integer.MIN_VALUE)).as("业务错误码不符合契约").isEqualTo(expectedCode);
        assertThat(root.path("message").asText("")).as("错误响应 message 不得为空").isNotBlank();
        JsonNode data = root.get("data");
        assertThat(data).as("错误响应必须保留 data 字段").isNotNull();
        if (expectedCode != 40001) {
            assertThat(data.isNull()).as("非参数校验错误的 data 必须为 null").isTrue();
        }
    }

    /**
     * 密码泄漏检查：允许 40001 的 fieldErrors 出现字段名 password，
     * 但绝不允许出现哈希字段、BCrypt 哈希或本次请求的明文密码。
     */
    private static void assertNoPasswordLeakage(ApiResult result) {
        String body = result.rawBody() == null ? "" : result.rawBody().toLowerCase(Locale.ROOT);
        assertThat(body.contains("password_hash") || body.contains("passwordhash"))
                .as("响应不得出现密码哈希字段").isFalse();
        assertThat(body.contains("$2a$") || body.contains("$2b$") || body.contains("$2y$"))
                .as("响应不得出现 BCrypt 哈希").isFalse();
        assertThat(body.contains(PASSWORD.toLowerCase(Locale.ROOT))).as("响应不得回显明文密码").isFalse();
    }

    private static JsonNode requireData(ApiResult result) {
        JsonNode data = result.json().get("data");
        assertThat(data != null && data.isObject()).as("成功响应 data 必须是 JSON 对象").isTrue();
        return data;
    }

    private static void assertNullableText(JsonNode data, String field) {
        JsonNode value = data.get(field);
        assertThat(value != null).as("安全资料必须保留字段 " + field + "（允许为 null）").isTrue();
        assertThat(value.isNull() || value.isTextual()).as(field + " 必须是字符串或 null").isTrue();
    }

    /** 校验当前用户安全投影恰为 8 个白名单字段，并返回字段名到文本值的映射。 */
    private static Map<String, String> assertSafeUserProjection(JsonNode data) {
        assertThat(data != null && data.isObject()).as("当前用户资料必须是 JSON 对象").isTrue();
        Set<String> fieldNames = new TreeSet<>();
        data.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames)
                .as("当前用户资料字段必须恰为 id/username/nickname/avatarUrl/bio/role/status/createdAt")
                .isEqualTo(new TreeSet<>(SAFE_USER_FIELDS));

        JsonNode id = data.get("id");
        assertThat(id != null && id.isTextual()).as("id 必须是 JSON 字符串").isTrue();
        assertThat(id.asText()).as("id 必须是十进制数字字符串").matches("\\d+");

        JsonNode username = data.get("username");
        assertThat(username != null && username.isTextual()).as("username 必须是 JSON 字符串").isTrue();
        assertThat(username.asText()).as("username 必须是规范化后的账号").matches("^[a-z0-9_]{3,50}$");

        JsonNode nickname = data.get("nickname");
        assertThat(nickname != null && nickname.isTextual()).as("nickname 必须是 JSON 字符串").isTrue();
        assertThat(nickname.asText()).as("nickname 不得为空白").isNotBlank();

        assertNullableText(data, "avatarUrl");
        assertNullableText(data, "bio");

        assertThat(data.get("role").asText()).as("安全投影 role 必须为 USER").isEqualTo("USER");
        assertThat(data.get("status").asText()).as("安全投影 status 必须为 ACTIVE").isEqualTo("ACTIVE");

        JsonNode createdAt = data.get("createdAt");
        assertThat(createdAt != null && createdAt.isTextual()).as("createdAt 必须是 JSON 字符串").isTrue();
        assertThat(createdAt.asText()).as("createdAt 必须是带 +08:00 的 ISO8601 字符串")
                .matches(SHANGHAI_TIMESTAMP_REGEX);
        assertThat(OffsetDateTime.parse(createdAt.asText()).getOffset())
                .as("createdAt 偏移必须为 +08:00").isEqualTo(SHANGHAI_OFFSET);

        Map<String, String> projection = new LinkedHashMap<>();
        data.fields().forEachRemaining(entry ->
                projection.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText()));
        return projection;
    }

    // ---------------------------------------------------------------------
    // 1. 注册：安全响应字段、BCrypt、默认昵称与规范化
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("注册 201：安全资料恰为 8 字段、id 字符串、role/status 固定，库中保存 BCrypt 哈希")
    void registrationCreatesSafeUserAndBcryptHash() {
        String username = nextUsername();
        ApiResult result = register(username, PASSWORD, "  旅行者  ");

        assertSuccess(result, 201);
        assertNoPasswordLeakage(result);
        JsonNode data = requireData(result);
        Map<String, String> projection = assertSafeUserProjection(data);
        assertThat(projection.get("username")).as("响应 username 必须等于规范化后的账号").isEqualTo(username);
        assertThat(projection.get("nickname")).as("登记昵称需 strip 首尾空白").isEqualTo("旅行者");
        assertThat(projection.get("avatarUrl")).as("新用户 avatarUrl 应为 null").isNull();
        assertThat(projection.get("bio")).as("新用户 bio 应为 null").isNull();

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertThat(storedHash).as("数据库必须保存 BCrypt 哈希").isNotNull();
        assertThat(PASSWORD.equals(storedHash)).as("数据库不得保存明文密码").isFalse();
        assertThat(storedHash.startsWith("$2")).as("密码哈希必须是 BCrypt 格式").isTrue();
        assertThat(BCRYPT.matches(PASSWORD, storedHash)).as("BCrypt 必须匹配原始密码").isTrue();

        Integer deleted = jdbc.queryForObject(
                "SELECT is_deleted FROM users WHERE username = ?", Integer.class, username);
        assertThat(deleted).as("新用户 is_deleted 必须为 0").isZero();
        String role = jdbc.queryForObject("SELECT role FROM users WHERE username = ?", String.class, username);
        String status = jdbc.queryForObject("SELECT status FROM users WHERE username = ?", String.class, username);
        assertThat(role).as("注册不允许客户端指定角色，数据库固定 USER").isEqualTo("USER");
        assertThat(status).as("注册不允许客户端指定状态，数据库固定 ACTIVE").isEqualTo("ACTIVE");
        Long storedId = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        assertThat(projection.get("id")).as("响应 id 必须是数据库实际自增 ID 的字符串形式")
                .isEqualTo(String.valueOf(storedId));

        // 注册不自动登录：不带 token 访问 me 仍然未登录。
        assertBusinessError(get("/auth/me"), 401, 40101);
    }

    @Test
    @DisplayName("注册规范化：账号 strip + 小写；缺省昵称等于规范化账号")
    void registrationNormalizesUsernameAndDefaultsNickname() {
        String rawUsername = "  " + testPrefix.toUpperCase(Locale.ROOT) + "_UPPER  ";
        String normalized = testPrefix + "_upper";
        ApiResult result = register(rawUsername, PASSWORD);

        assertSuccess(result, 201);
        Map<String, String> projection = assertSafeUserProjection(requireData(result));
        assertThat(projection.get("username")).as("username 必须 strip 并转小写").isEqualTo(normalized);
        assertThat(projection.get("nickname")).as("缺省昵称等于规范化账号").isEqualTo(normalized);

        String storedUsername = jdbc.queryForObject(
                "SELECT username FROM users WHERE username = ?", String.class, normalized);
        assertThat(storedUsername).as("数据库中只保存规范化账号").isEqualTo(normalized);
    }

    @Test
    @DisplayName("登录不 trim 密码：原样密码可登录，去掉首尾空白的变形不可登录")
    void registrationDoesNotTrimPassword() {
        String username = nextUsername();
        String spacedPassword = "  M1-Pass-2026  ";
        assertSuccess(register(username, spacedPassword), 201);

        ApiResult exact = login(username, spacedPassword);
        assertSuccess(exact, 200);
        assertThat(requireTokenValue(exact)).as("tokenValue 不能为空").isNotBlank();

        assertBusinessError(login(username, spacedPassword.trim()), 401, 40102);
    }

    @Test
    @DisplayName("逻辑删除账号仍占用唯一账号：重复注册返回 409/40901 且既有记录不被改写")
    void registrationRejectsUsernameOccupiedByLogicallyDeletedUser() {
        String occupiedUsername = testPrefix + "_deleted";
        String preservedHash = "$2a$10$abcdefghijklmnopqrstuv";
        jdbc.update("INSERT INTO users (username, password_hash, nickname, role, status, is_deleted) "
                + "VALUES (?, ?, ?, 'USER', 'ACTIVE', 1)", occupiedUsername, preservedHash, "已删除用户");

        ApiResult result = register(occupiedUsername, PASSWORD);
        assertBusinessError(result, 409, 40901);
        assertNoPasswordLeakage(result);

        Map<String, Object> untouched = jdbc.queryForMap(
                "SELECT CAST(is_deleted AS SIGNED) AS is_deleted, nickname, password_hash FROM users WHERE username = ?", occupiedUsername);
        assertThat(((Number) untouched.get("is_deleted")).intValue()).as("既有逻辑删除标记不得被改写").isEqualTo(1);
        assertThat(untouched.get("nickname")).as("既有记录昵称不得被改写").isEqualTo("已删除用户");
        assertThat(untouched.get("password_hash")).as("既有记录密码哈希不得被改写").isEqualTo(preservedHash);
    }

    // ---------------------------------------------------------------------
    // 2. 注册：顺序与并发冲突
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("同一账号顺序重复注册：第二次 409/40901，且只保留一条用户记录")
    void registrationRejectsSequentialDuplicateUsername() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);

        ApiResult duplicate = register(username, PASSWORD + "-other");
        assertBusinessError(duplicate, 409, 40901);
        assertNoPasswordLeakage(duplicate);
        assertThat(countUsers(username)).as("重复注册不得产生第二条记录").isEqualTo(1);
    }

    @Test
    @DisplayName("同一账号并发注册（含大小写/空白变体）：恰好一个 201，其余 409/40901")
    void registrationRejectsConcurrentDuplicateUsername() throws Exception {
        String username = testPrefix + "_race";
        List<String> variantList = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REGISTRATIONS; i++) {
            // 一半使用大小写+空白变体，验证规范化后仍由唯一键拒绝。
            variantList.add(i % 2 == 1 ? " " + username.toUpperCase(Locale.ROOT) + " " : username);
        }
        String[] variants = variantList.toArray(new String[0]);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<ApiResult> unexpected = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(variants.length);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String variant : variants) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        startGate.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    ApiResult result = register(variant, PASSWORD);
                    if (result.status() == 201) {
                        successCount.incrementAndGet();
                    } else if (result.status() == 409 && result.json().path("code").asInt() == 40901) {
                        conflictCount.incrementAndGet();
                    } else {
                        synchronized (unexpected) {
                            unexpected.add(result);
                        }
                    }
                }, executor));
            }
            startGate.countDown();
            // 先 join 全部辅助线程，再进入清理与断言。
            for (CompletableFuture<Void> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } catch (ExecutionException exception) {
            fail("并发注册辅助线程异常: " + exception.getCause());
        } finally {
            executor.shutdownNow();
        }
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                .as("并发注册辅助线程必须在清理前全部结束").isTrue();

        assertThat(unexpected.size())
                .as("并发冲突只能返回 201 或 409/40901；实际不符合契约的响应数")
                .isZero();
        assertThat(successCount.get()).as("并发重复注册必须恰好一个成功").isEqualTo(1);
        assertThat(conflictCount.get()).as("其余并发注册必须全部冲突").isEqualTo(variants.length - 1);
        assertThat(countUsers(username)).as("并发注册后库中必须只有一条记录").isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // 3. 注册：密码 Unicode 码点 / 空白 / UTF-8 72 字节边界
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("密码不足 8 个 Unicode 码点：400/40001 且不写用户")
    void registrationRejectsPasswordShorterThanEightUnicodeCodePoints() {
        String username = nextUsername();
        // 7 个码点（含 emoji，按码点而不是 UTF-16 单元或字节计数）。
        assertRegistrationRejected(username, "A1b2C3\uD83D\uDE00", "7 个码点的密码");
        assertThat(countUsers(username)).as("被拒绝的注册不得产生用户记录").isZero();

        // 边界内：恰好 8 个 Unicode 码点可以注册（含多字节字符，仍按码点计数）。
        // A,1,b,2,c,3 = 6 个码点 + 1 个 emoji（2 个 UTF-16 单元，按 1 个码点计）+ 1 个汉字 = 8 码点。
        String exactlyEight = "A1b2c3\uD83D\uDE00\u4E2D";
        assertThat(exactlyEight.codePointCount(0, exactlyEight.length())).isEqualTo(8);
        String boundaryUsername = nextUsername();
        assertSuccess(register(boundaryUsername, exactlyEight), 201);

        // 同一账号用合法密码仍可注册，说明失败没有留下半成品记录。
        assertSuccess(register(username, PASSWORD), 201);
    }

    @Test
    @DisplayName("密码全空白：400/40001 且不写用户")
    void registrationRejectsAllWhitespacePassword() {
        String username = nextUsername();
        assertRegistrationRejected(username, "        ", "8 个空格的密码");
        assertRegistrationRejected(username, "\t\t\t\t\t\t", "6 个制表符的密码");
        assertThat(countUsers(username)).as("空白密码不得产生用户记录").isZero();
    }

    @Test
    @DisplayName("密码 UTF-8 72 字节边界：72 字节可用，超过 72 字节 400 且不写用户")
    void registrationEnforcesPasswordUtf8ByteLimit() {
        String username72 = nextUsername();
        String cjk72Bytes = "密".repeat(24); // 24 码点 × 3 字节 = 72 字节
        assertThat(cjk72Bytes.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertSuccess(register(username72, cjk72Bytes), 201);

        String ascii72 = "a".repeat(72);
        String usernameAscii72 = nextUsername();
        assertSuccess(register(usernameAscii72, ascii72), 201);

        String username73 = nextUsername();
        assertRegistrationRejected(username73, "a".repeat(73), "73 个 ASCII 字节");
        assertThat(countUsers(username73)).as("超长密码不得产生用户记录").isZero();

        String username75 = nextUsername();
        assertRegistrationRejected(username75, "密".repeat(25), "75 个 UTF-8 字节");
        assertThat(countUsers(username75)).as("超长多字节密码不得产生用户记录").isZero();

        String usernameEmoji = nextUsername();
        // 18 个 emoji 各 4 字节 = 72 字节（18 个码点 >= 8），边界内应可注册。
        assertSuccess(register(usernameEmoji, "\uD83D\uDE00".repeat(18)), 201);
    }

    private void assertRegistrationRejected(String username, String password, String description) {
        ApiResult result = register(username, password);
        assertBusinessError(result, 400, 40001);
        assertNoPasswordLeakage(result);
        if (!password.isBlank()) {
            String body = result.rawBody() == null ? "" : result.rawBody();
            assertThat(body.contains(password))
                    .as(description + " 的响应不得回显被拒绝的密码").isFalse();
        }
    }

    // ---------------------------------------------------------------------
    // 4. 注册：严格 JSON 类型与字段白名单
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("注册字符串字段拒绝数值/布尔隐式转换：400/40001 且不写用户")
    void registrationRejectsScalarToTextCoercion() {
        String username = nextUsername();
        assertBusinessError(post("/auth/register",
                "{\"username\":12345,\"password\":\"" + PASSWORD + "\"}"), 400, 40001);
        assertBusinessError(post("/auth/register",
                "{\"username\":true,\"password\":\"" + PASSWORD + "\"}"), 400, 40001);
        assertBusinessError(post("/auth/register",
                "{\"username\":\"" + nextUsername() + "\",\"password\":20260101}"), 400, 40001);
        assertBusinessError(post("/auth/register",
                "{\"username\":\"" + nextUsername() + "\",\"password\":false}"), 400, 40001);
        assertBusinessError(post("/auth/register",
                "{\"username\":\"" + nextUsername() + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":42}"),
                400, 40001);
        // 必填字段缺失与请求体结构非法同样返回 400。
        assertBusinessError(post("/auth/register", "{\"password\":\"" + PASSWORD + "\"}"), 400, 40001);
        assertBusinessError(post("/auth/register", "{\"username\":\"" + nextUsername() + "\"}"), 400, 40001);
        assertBusinessError(post("/auth/register", "[]"), 400, 40001);
        assertBusinessError(post("/auth/register", "null"), 400, 40001);
        assertThat(countUsers(username)).as("类型错误不得产生用户记录").isZero();
    }

    @Test
    @DisplayName("注册拒绝未知字段与权限字段注入（role/userId/password_hash/status/isDeleted）：400 且不写用户")
    void registrationRejectsPrivilegedAndUnknownFields() {
        String[] injectedSnippets = {
                "\"role\":\"ADMIN\"",
                "\"userId\":\"1\"",
                "\"password_hash\":\"$2a$10$abcdefghijklmnopqrstuv\"",
                "\"status\":\"DISABLED\"",
                "\"isDeleted\":1",
                "\"id\":\"1\"",
                "\"createdAt\":\"2026-11-02T00:00:00.000+08:00\"",
                "\"updatedAt\":\"2026-11-02T00:00:00.000+08:00\"",
                "\"authorId\":\"1\"",
                "\"unknownField\":\"x\""
        };
        for (String snippet : injectedSnippets) {
            String username = nextUsername();
            String body = "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"," + snippet + "}";
            ApiResult result = post("/auth/register", body);
            assertBusinessError(result, 400, 40001);
            String rejectedBody = result.rawBody() == null ? "" : result.rawBody();
            assertThat(rejectedBody.contains("$2a$10$")).as("错误响应不得回显被注入的密码哈希").isFalse();
            assertThat(countUsers(username)).as("注入 " + snippet + " 的注册不得写入用户").isZero();
        }
    }

    // ---------------------------------------------------------------------
    // 5. 登录：通用失败、禁用/删除账号、token 与 me 投影
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("登录失败通用化：未知账号与错误密码均 401/40102 且提示一致，不泄漏账号是否存在")
    void loginFailuresAreGeneric() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);

        ApiResult unknownAccount = login(nextUsername(), PASSWORD);
        ApiResult wrongPassword = login(username, "Wrong-Password-2026");

        assertBusinessError(unknownAccount, 401, 40102);
        assertBusinessError(wrongPassword, 401, 40102);
        assertThat(unknownAccount.json().path("message").asText())
                .as("未知账号与错误密码的提示必须一致，避免账号枚举")
                .isEqualTo(wrongPassword.json().path("message").asText());
        assertNoPasswordLeakage(wrongPassword);

        // 登录请求同样只接受白名单字段。
        assertBusinessError(post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}"),
                400, 40001);
        assertBusinessError(post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"x\"}"),
                400, 40001);
        assertBusinessError(post("/auth/login", "{\"username\":\"" + username + "\"}"), 400, 40001);
        assertBusinessError(post("/auth/login", "{\"password\":\"" + PASSWORD + "\"}"), 400, 40001);
    }

    @Test
    @DisplayName("禁用或逻辑删除的账号登录：统一 401/40102，不暴露账号状态")
    void loginRejectsDisabledAndDeletedAccounts() {
        String disabledUsername = nextUsername();
        assertSuccess(register(disabledUsername, PASSWORD), 201);
        assertSuccess(login(disabledUsername, PASSWORD), 200);
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE username = ?", disabledUsername);
        ApiResult disabledLogin = login(disabledUsername, PASSWORD);
        assertBusinessError(disabledLogin, 401, 40102);

        String deletedUsername = nextUsername();
        assertSuccess(register(deletedUsername, PASSWORD), 201);
        assertSuccess(login(deletedUsername, PASSWORD), 200);
        jdbc.update("UPDATE users SET is_deleted = 1 WHERE username = ?", deletedUsername);
        ApiResult deletedLogin = login(deletedUsername, PASSWORD);
        assertBusinessError(deletedLogin, 401, 40102);

        ApiResult unknownAccount = login(nextUsername(), PASSWORD);
        assertThat(disabledLogin.json().path("message").asText())
                .as("禁用账号的登录提示必须与未知账号一致").isEqualTo(unknownAccount.json().path("message").asText());
        assertThat(deletedLogin.json().path("message").asText())
                .as("删除账号的登录提示必须与未知账号一致").isEqualTo(unknownAccount.json().path("message").asText());
    }

    @Test
    @DisplayName("登录成功：tokenName=satoken、token 非空；GET /auth/me 与 /users/me 投影一致且等于登录 user")
    void loginReturnsSatokenTokenAndMeProjectionMatches() {
        String username = nextUsername();
        register(username, PASSWORD);
        ApiResult loginResult = login(username, PASSWORD);

        assertSuccess(loginResult, 200);
        assertNoPasswordLeakage(loginResult);
        JsonNode loginData = requireData(loginResult);
        Set<String> loginFieldNames = new TreeSet<>();
        loginData.fieldNames().forEachRemaining(loginFieldNames::add);
        assertThat(loginFieldNames).as("登录响应 data 字段必须恰为 tokenName/tokenValue/user")
                .isEqualTo(new TreeSet<>(Set.of("tokenName", "tokenValue", "user")));
        assertThat(loginData.path("tokenName").asText()).as("tokenName 固定为 satoken").isEqualTo("satoken");
        String token = requireTokenValue(loginResult);
        assertThat(token.length() >= 16).as("tokenValue 必须有足够长度且不是空串").isTrue();
        assertThat(PASSWORD.equals(token)).as("token 不得等于明文密码").isFalse();

        JsonNode loginUser = loginData.get("user");
        Map<String, String> loginProjection = assertSafeUserProjection(loginUser);

        ApiResult authMe = getWithToken("/auth/me", token);
        assertSuccess(authMe, 200);
        assertNoPasswordLeakage(authMe);
        Map<String, String> authMeProjection = assertSafeUserProjection(requireData(authMe));

        ApiResult usersMe = getWithToken("/users/me", token);
        assertSuccess(usersMe, 200);
        Map<String, String> usersMeProjection = assertSafeUserProjection(requireData(usersMe));

        assertThat(authMeProjection).as("/auth/me 投影必须与登录返回的 user 完全一致").isEqualTo(loginProjection);
        assertThat(usersMeProjection).as("/users/me 投影必须与 /auth/me 完全一致").isEqualTo(authMeProjection);
        assertThat(authMeProjection.get("username")).isEqualTo(username);
    }

    @Test
    @DisplayName("匿名访问受保护端点：me/users me/PATCH/logout 均 401/40101 且 data=null")
    void anonymousRequestsAreRejected() {
        assertBusinessError(get("/auth/me"), 401, 40101);
        assertBusinessError(get("/users/me"), 401, 40101);
        assertBusinessError(request("PATCH", "/users/me", null,
                "{\"nickname\":\"Anonymous\"}", Map.of()), 401, 40101);
        assertBusinessError(post("/auth/logout", "{}"), 401, 40101);

        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        assertThat(countUsers(username)).as("匿名请求不得意外创建或改写用户").isEqualTo(1);
    }

    @Test
    @DisplayName("多会话：两次登录 token 不同；退出仅当前 token 失效，另一 token 继续可用")
    void multipleLoginsIssueIndependentTokensAndLogoutOnlyInvalidatesCurrent() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String firstToken = requireTokenValue(login(username, PASSWORD));
        String secondToken = requireTokenValue(login(username, PASSWORD));
        assertThat(!firstToken.equals(secondToken)).as("同一用户两次登录必须产生不同 token").isTrue();
        assertSuccess(getWithToken("/auth/me", firstToken), 200);
        assertSuccess(getWithToken("/auth/me", secondToken), 200);

        ApiResult logout = postWithToken("/auth/logout", firstToken, null);
        assertSuccess(logout, 200);
        assertThat(logout.json().path("data").isNull()).as("logout 成功 data 必须为 null").isTrue();

        assertBusinessError(getWithToken("/auth/me", firstToken), 401, 40101);
        assertSuccess(getWithToken("/auth/me", secondToken), 200);
    }

    // ---------------------------------------------------------------------
    // 6. token 只从 satoken 请求头读取；CORS
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("token 仅在 satoken 请求头生效：Cookie、查询参数、请求体携带同一 token 仍为 401/40101")
    void tokenIsOnlyReadFromSatokenHeader() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String token = requireTokenValue(login(username, PASSWORD));
        assertSuccess(getWithToken("/auth/me", token), 200);

        ApiResult viaCookie = request("GET", "/auth/me", null, null, Map.of("Cookie", "satoken=" + token));
        assertBusinessError(viaCookie, 401, 40101);

        ApiResult viaQuery = request("GET", "/auth/me?satoken=" + urlEncode(token), null, null, Map.of());
        assertBusinessError(viaQuery, 401, 40101);

        ApiResult viaBody = request("POST", "/auth/logout", null,
                "{\"satoken\":\"" + token + "\"}", Map.of());
        assertBusinessError(viaBody, 401, 40101);

        // 其他常见非法头名也不应被接受。
        ApiResult viaAuthorization = request("GET", "/auth/me", null, null,
                Map.of("Authorization", "Bearer " + token));
        assertBusinessError(viaAuthorization, 401, 40101);

        // 头内 token 依然有效，证明上面的 401 不是 token 本身失效。
        assertSuccess(getWithToken("/auth/me", token), 200);
    }

    @Test
    @DisplayName("CORS：允许 localhost:5173 的预检与实际请求，拒绝未允许来源")
    void corsAllowsConfiguredOriginOnly() {
        ApiResult preflight = preflight("/auth/login", ALLOWED_ORIGIN, "POST", "satoken,content-type");
        assertThat(preflight.status()).as("允许来源的预检必须成功（2xx）").isBetween(200, 299);
        String allowOrigin = preflight.header("Access-Control-Allow-Origin");
        assertThat(allowOrigin).as("必须回显允许来源").isEqualTo(ALLOWED_ORIGIN);
        String allowMethods = headerOrEmpty(preflight, "Access-Control-Allow-Methods").toUpperCase(Locale.ROOT);
        assertThat(allowMethods).as("预检必须允许 POST").contains("POST");
        String allowHeaders = headerOrEmpty(preflight, "Access-Control-Allow-Headers").toLowerCase(Locale.ROOT);
        assertThat(allowHeaders).as("预检必须允许 satoken 请求头").contains("satoken");
        assertThat(headerOrEmpty(preflight, "Access-Control-Allow-Credentials"))
                .as("不得启用凭据模式").isNotEqualTo("true");

        ApiResult allowedActual = request("GET", "/auth/me", null, null, Map.of("Origin", ALLOWED_ORIGIN));
        assertThat(allowedActual.header("Access-Control-Allow-Origin"))
                .as("允许来源的实际请求必须返回 ACAO 头").isEqualTo(ALLOWED_ORIGIN);

        ApiResult deniedPreflight = preflight("/auth/login", DENIED_ORIGIN, "POST", "satoken");
        String deniedAllowOrigin = deniedPreflight.header("Access-Control-Allow-Origin");
        assertThat(deniedAllowOrigin == null || !deniedAllowOrigin.equals(DENIED_ORIGIN))
                .as("未允许来源不得获得其 Origin 回显").isTrue();

        ApiResult deniedActual = request("GET", "/auth/me", null, null, Map.of("Origin", DENIED_ORIGIN));
        String deniedActualAllowOrigin = deniedActual.header("Access-Control-Allow-Origin");
        assertThat(deniedActualAllowOrigin == null || !deniedActualAllowOrigin.equals(DENIED_ORIGIN))
                .as("未允许来源的实际请求不得获得 ACAO 头").isTrue();
    }

    private ApiResult preflight(String path, String origin, String requestMethod, String requestHeaders) {
        return request("OPTIONS", path, null, null, Map.of(
                "Origin", origin,
                "Access-Control-Request-Method", requestMethod,
                "Access-Control-Request-Headers", requestHeaders));
    }

    private static String headerOrEmpty(ApiResult result, String name) {
        String value = result.header(name);
        return value == null ? "" : value;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------
    // 7. PATCH /users/me
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("PATCH 部分更新：缺失字段保持、昵称 strip、bio 与 avatarUrl 可置 null 清空")
    void profilePatchKeepsMissingFieldsAndClearsNullableFields() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String token = requireTokenValue(login(username, PASSWORD));

        ApiResult seeded = patchProfile(token, body -> body
                .put("nickname", "  设置昵称  ")
                .put("bio", "简介内容")
                .put("avatarUrl", VALID_AVATAR_PATH));
        assertSuccess(seeded, 200);
        Map<String, String> seededProjection = assertSafeUserProjection(requireData(seeded));
        assertThat(seededProjection.get("nickname")).as("昵称需 strip 首尾空白").isEqualTo("设置昵称");
        assertThat(seededProjection.get("bio")).isEqualTo("简介内容");
        assertThat(seededProjection.get("avatarUrl")).as("既有本地上传图片路径可设置为头像")
                .isEqualTo(VALID_AVATAR_PATH);

        ApiResult nicknameOnly = patchProfile(token, body -> body.put("nickname", "新昵称"));
        assertSuccess(nicknameOnly, 200);
        Map<String, String> afterNickname = assertSafeUserProjection(requireData(nicknameOnly));
        assertThat(afterNickname.get("nickname")).isEqualTo("新昵称");
        assertThat(afterNickname.get("bio")).as("未提供的 bio 必须保持原值").isEqualTo("简介内容");
        assertThat(afterNickname.get("avatarUrl")).as("未提供的 avatarUrl 必须保持原值").isEqualTo(VALID_AVATAR_PATH);

        ApiResult clearBio = patchProfile(token, body -> body.putNull("bio"));
        assertSuccess(clearBio, 200);
        Map<String, String> afterClearBio = assertSafeUserProjection(requireData(clearBio));
        assertThat(afterClearBio.get("bio")).as("bio=null 清空简介").isNull();
        assertThat(afterClearBio.get("nickname")).as("清空 bio 不影响昵称").isEqualTo("新昵称");
        assertThat(afterClearBio.get("avatarUrl")).isEqualTo(VALID_AVATAR_PATH);

        ApiResult clearAvatar = patchProfile(token, body -> body.putNull("avatarUrl"));
        assertSuccess(clearAvatar, 200);
        Map<String, String> afterClearAvatar = assertSafeUserProjection(requireData(clearAvatar));
        assertThat(afterClearAvatar.get("avatarUrl")).as("avatarUrl=null 清空头像").isNull();
        assertThat(afterClearAvatar.get("bio")).isNull();
        assertThat(afterClearAvatar.get("nickname")).isEqualTo("新昵称");

        // 持久化：重新读取 me 仍为清空后的状态。
        Map<String, String> reloaded = assertSafeUserProjection(requireData(getWithToken("/users/me", token)));
        assertThat(reloaded.get("nickname")).isEqualTo("新昵称");
        assertThat(reloaded.get("bio")).isNull();
        assertThat(reloaded.get("avatarUrl")).isNull();
    }

    @Test
    @DisplayName("PATCH 非法请求体：空对象、nickname=null/空白/超长、未知字段均 400 且不落库")
    void profilePatchRejectsInvalidBodies() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String token = requireTokenValue(login(username, PASSWORD));

        assertBusinessError(request("PATCH", "/users/me", token, "{}", Map.of()), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.putNull("nickname")), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("nickname", "")), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("nickname", "   ")), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("nickname", "长".repeat(51))), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("bio", "简".repeat(301))), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("nickname", 12345)), 400, 40001);
        assertBusinessError(patchProfile(token, body -> body.put("bio", true)), 400, 40001);
        assertBusinessError(request("PATCH", "/users/me", token, "[]", Map.of()), 400, 40001);
        assertBusinessError(request("PATCH", "/users/me", token, "null", Map.of()), 400, 40001);
        assertBusinessError(request("PATCH", "/users/me", token, "not-json", Map.of()), 400, 40001);

        Map<String, String> projection = assertSafeUserProjection(requireData(getWithToken("/users/me", token)));
        assertThat(projection.get("nickname")).as("被拒绝的 PATCH 不得修改昵称").isEqualTo(username);
        assertThat(projection.get("bio")).as("被拒绝的 PATCH 不得修改简介").isNull();
    }

    @Test
    @DisplayName("PATCH 字段白名单：role/userId/password_hash/status/isDeleted/未知字段/存在标记注入均 400")
    void profilePatchRejectsPrivilegedFieldsAndExistenceMarkers() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String token = requireTokenValue(login(username, PASSWORD));

        ObjectNode[] injected = {
                object().put("role", "ADMIN"),
                object().put("userId", "1"),
                object().put("password_hash", "$2a$10$abcdefghijklmnopqrstuv"),
                object().put("status", "DISABLED"),
                object().put("isDeleted", 1),
                object().put("id", "1"),
                object().put("username", "hijack_user"),
                object().put("createdAt", "2026-11-02T00:00:00.000+08:00"),
                object().put("authorId", "1"),
                object().put("unknownField", "x"),
                object().put("nicknamePresent", true),
                object().put("nicknameSet", true),
                object().put("hasNickname", true),
                object().put("__nickname", "x")
        };
        for (ObjectNode body : injected) {
            body.put("nickname", "仍应被拒绝");
            ApiResult result = request("PATCH", "/users/me", token, write(body), Map.of());
            assertBusinessError(result, 400, 40001);
            String rejectedBody = result.rawBody() == null ? "" : result.rawBody();
            assertThat(rejectedBody.contains("$2a$10$")).as("错误响应不得回显被注入的密码哈希").isFalse();
        }

        Map<String, String> projection = assertSafeUserProjection(requireData(getWithToken("/users/me", token)));
        assertThat(projection.get("username")).as("username 不可通过 PATCH 修改").isEqualTo(username);
        assertThat(projection.get("nickname")).as("注入请求不得生效").isEqualTo(username);
        assertThat(projection.get("role")).isEqualTo("USER");
        assertThat(projection.get("status")).isEqualTo("ACTIVE");
        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, username);
        assertThat(BCRYPT.matches(PASSWORD, storedHash)).as("注入不得改写密码哈希").isTrue();
    }

    @Test
    @DisplayName("PATCH avatarUrl 路径校验：外链/穿越/不存在/非图片扩展名拒绝，合法本地 PNG 可设置与清空")
    void profilePatchValidatesManagedAvatarPaths() {
        String username = nextUsername();
        assertSuccess(register(username, PASSWORD), 201);
        String token = requireTokenValue(login(username, PASSWORD));

        String[] invalidPaths = {
                "https://evil.example.com/avatar.png",
                "http://localhost:8080/uploads/valid-avatar.png",
                "/uploads/../../etc/passwd",
                "..%2F..%2Fetc%2Fpasswd",
                "/uploads/sub/../../etc/passwd",
                "/uploads/missing-avatar.png",
                INVALID_AVATAR_PATH,
                "uploads/valid-avatar.png",
                ""
        };
        for (String invalidPath : invalidPaths) {
            ApiResult result = patchProfile(token, body -> body.put("avatarUrl", invalidPath));
            assertBusinessError(result, 400, 40001);
            String rejectedBody = result.rawBody() == null ? "" : result.rawBody();
            assertThat(!invalidPath.isEmpty() && rejectedBody.contains(invalidPath))
                    .as("错误响应不得回显被拒绝的头像路径").isFalse();
        }

        Map<String, String> projection = assertSafeUserProjection(requireData(getWithToken("/users/me", token)));
        assertThat(projection.get("avatarUrl")).as("被拒绝的路径不得写入头像").isNull();

        ApiResult valid = patchProfile(token, body -> body.put("avatarUrl", VALID_AVATAR_PATH));
        assertSuccess(valid, 200);
        assertThat(assertSafeUserProjection(requireData(valid)).get("avatarUrl"))
                .as("存在的受管本地 PNG 可设置为头像").isEqualTo(VALID_AVATAR_PATH);

        ApiResult cleared = patchProfile(token, body -> body.putNull("avatarUrl"));
        assertSuccess(cleared, 200);
        assertThat(assertSafeUserProjection(requireData(cleared)).get("avatarUrl")).isNull();
    }

    // ---------------------------------------------------------------------
    // 8. 禁用/删除后旧 token 失效；大 ID 与时间格式
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("禁用或逻辑删除后既有 token 立即 401/40101，且不能继续修改资料")
    void disabledOrDeletedUserLosesExistingToken() {
        String disabledUsername = nextUsername();
        assertSuccess(register(disabledUsername, PASSWORD), 201);
        String disabledToken = requireTokenValue(login(disabledUsername, PASSWORD));
        assertSuccess(getWithToken("/auth/me", disabledToken), 200);

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE username = ?", disabledUsername);
        assertBusinessError(getWithToken("/auth/me", disabledToken), 401, 40101);
        assertBusinessError(getWithToken("/users/me", disabledToken), 401, 40101);
        assertBusinessError(patchProfile(disabledToken, body -> body.put("nickname", "Disabled")), 401, 40101);

        String deletedUsername = nextUsername();
        assertSuccess(register(deletedUsername, PASSWORD), 201);
        String deletedToken = requireTokenValue(login(deletedUsername, PASSWORD));
        assertSuccess(getWithToken("/auth/me", deletedToken), 200);
        jdbc.update("UPDATE users SET is_deleted = 1 WHERE username = ?", deletedUsername);
        assertBusinessError(getWithToken("/auth/me", deletedToken), 401, 40101);
        assertBusinessError(patchProfile(deletedToken, body -> body.put("nickname", "Deleted")), 401, 40101);
    }

    @Test
    @DisplayName("大于 2^53-1 的真实用户 ID 在 JSON 中仍为字符串；createdAt 为 +08:00 毫秒时间")
    void largeUserIdAndOffsetTimestampAreSerializedAsStrings() {
        String username = testPrefix + "_bigid";
        String passwordHash = BCRYPT.encode(PASSWORD);
        Long currentMaxId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM users", Long.class);
        long largeId = Math.max(LARGE_ID_FLOOR, (currentMaxId == null ? 0L : currentMaxId) + 1);
        // 只插入并删除本测试自己的 >2^53 夹具；不改 DDL、不触碰他人记录。
        jdbc.update("INSERT INTO users (id, username, password_hash, nickname, role, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', 0)", largeId, username, passwordHash, "大ID用户");
        try {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, largeId);
            assertThat(existing).as("测试库应能承载大于 2^53-1 的用户 ID").isEqualTo(1);

            ApiResult loginResult = login(username, PASSWORD);
            assertSuccess(loginResult, 200);
            JsonNode loginUser = requireData(loginResult).get("user");
            JsonNode idNode = loginUser.get("id");
            assertThat(idNode.isTextual()).as("大 ID 必须序列化为 JSON 字符串").isTrue();
            assertThat(idNode.asText()).as("大 ID 字符串不得丢失精度").isEqualTo(Long.toString(largeId));
            assertThat(loginUser.get("createdAt").asText())
                    .as("createdAt 必须是带 +08:00 的 ISO8601 字符串")
                    .matches(SHANGHAI_TIMESTAMP_REGEX);

            String token = requireTokenValue(loginResult);
            Map<String, String> projection = assertSafeUserProjection(requireData(getWithToken("/auth/me", token)));
            assertThat(projection.get("id")).as("/auth/me 的大 ID 也必须是字符串").isEqualTo(Long.toString(largeId));

            String storedCreatedAt = jdbc.queryForObject(
                    "SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f') FROM users WHERE id = ?",
                    String.class, largeId);
            LocalDateTime databaseTime = LocalDateTime.parse(storedCreatedAt, DATABASE_TIMESTAMP_FORMAT);
            OffsetDateTime responseTime = OffsetDateTime.parse(projection.get("createdAt"));
            OffsetDateTime expected = databaseTime.atOffset(SHANGHAI_OFFSET);
            // DATETIME(3) 精确到毫秒；容差 5ms 用于发现「截断到秒」的实现（若截断则至少差数百毫秒），
            // 同时不依赖小数位是否省略尾零。
            assertThat(Duration.between(expected, responseTime).abs())
                    .as("响应 createdAt 必须是数据库时间按 +08:00 解释且保留毫秒精度（容差 5ms）")
                    .isLessThanOrEqualTo(Duration.ofMillis(5));
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ? AND username = ?", largeId, username);
        }
    }

    private static String requireTokenValue(ApiResult result) {
        JsonNode data = requireData(result);
        JsonNode tokenValue = data.get("tokenValue");
        assertThat(tokenValue != null && tokenValue.isTextual())
                .as("登录响应 data.tokenValue 必须是非空字符串").isTrue();
        String token = tokenValue.asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
