package com.example.travelplatform.destination;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.example.travelplatform.testsupport.HttpTestSupport.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import javax.sql.DataSource;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DestinationApiTest {
    private static final String ADMIN = "/api/v1/admin/destinations";
    private static final String PUBLIC = "/api/v1/destinations";
    private static final String[] WRITE_FIELDS = {"name", "destinationType", "parentId", "country", "province", "city",
            "description", "coverImageUrl", "recommendedSeasons", "status", "sortOrder"};
    private static final Path UPLOAD_ROOT = privateRoot();
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;

    private static Path privateRoot() {
        try {
            Path root = Files.createTempDirectory("travel-destination-api-").toAbsolutePath().normalize();
            ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", root.resolve("cover.png").toFile());
            return root;
        } catch (Exception exception) { throw new IllegalStateException("Cannot create private image fixture"); }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("travel.storage.upload-dir", UPLOAD_ROOT::toString);
    }
    @BeforeEach void fixtures() { h = new HttpTestSupport(source, port, "m2d"); }
    @AfterEach void cleanup() {
        if (h != null) { try { h.cleanupDestinations(); } finally { h.cleanupAccounts(); } }
    }
    @AfterAll static void cleanFiles() throws Exception {
        assertThat(UPLOAD_ROOT.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())).isTrue();
        assertThat(UPLOAD_ROOT.getFileName().toString()).startsWith("travel-destination-api-");
        try (var paths = Files.walk(UPLOAD_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private Map<String, Object> body(String suffix, Map<String, ?> extras) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", h.named(suffix)); body.putAll(extras); return body;
    }
    private JsonNode create(Account admin, String suffix, Map<String, ?> extras) {
        return success(h.json("POST", ADMIN, admin.token(), body(suffix, extras)), 201);
    }
    private Map<String, Object> full(JsonNode destination) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : WRITE_FIELDS) {
            JsonNode value = destination.get(key);
            body.put(key, value == null || value.isNull() ? null : key.equals("sortOrder") ? value.intValue() : value.asText());
        }
        return body;
    }
    private Result get(String path) { return h.request("GET", path, null, null, Map.of()); }
    private Result put(Account admin, JsonNode destination, Map<String, Object> body) {
        return h.json("PUT", ADMIN + "/" + destination.path("id").asText(), admin.token(), body);
    }
    private JsonNode state(Account admin, JsonNode destination, String status) {
        return success(h.json("PATCH", ADMIN + "/" + destination.path("id").asText() + "/status", admin.token(),
                Map.of("status", status)), 200);
    }
    private String search(String value) { return PUBLIC + "?keyword=" + URLEncoder.encode(value, StandardCharsets.UTF_8); }

    @Test void administratorCreatesDestinationWithSafeProjection() {
        Account admin = h.account(true);
        JsonNode value = create(admin, "created", Map.of());
        Set<String> names = new HashSet<>(); value.fieldNames().forEachRemaining(names::add);
        Set<String> expected = new HashSet<>(List.of(WRITE_FIELDS));
        expected.addAll(Set.of("id", "createdAt", "updatedAt"));
        expected.addAll(Set.of("address","longitude","latitude","recommendedDurationMinutes","recommendedDays","openingHours","ticketInfo","officialUrl","sourceUrl","verifiedAt","images"));
        assertThat(names).isEqualTo(expected);
        assertThat(value.path("id").isTextual()).isTrue();
        assertThat(value.path("id").asText()).matches("[1-9][0-9]*");
        assertThat(value.path("name").asText()).isEqualTo(h.named("created"));
        assertThat(value.path("destinationType").asText()).isEqualTo("CITY");
        assertThat(value.path("country").asText()).isEqualTo("中国");
        assertThat(value.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(value.path("sortOrder").isIntegralNumber()).isTrue();
        assertThat(value.path("sortOrder").asInt()).isZero();
        assertThat(value.path("parentId").isNull()).isTrue();
        assertThat(value.path("createdAt").asText()).matches(".*T.*\\.\\d{3}\\+08:00");
    }

    @Test void publicPaginationFiltersVisibilityAndKeepsInactiveDetails() {
        Account admin = h.account(true);
        JsonNode a = create(admin, "a", Map.of("sortOrder", 2));
        JsonNode b = create(admin, "b", Map.of("status", "INACTIVE", "sortOrder", 0));
        JsonNode c = create(admin, "c", Map.of("destinationType", "OTHER", "sortOrder", 1));
        JsonNode first = success(get(search(h.prefix()) + "&page=1&size=1"), 200);
        for (String key : List.of("total", "current", "size", "pages")) assertThat(first.path(key).isIntegralNumber()).isTrue();
        assertThat(first.path("total").asLong()).isEqualTo(2);
        assertThat(first.path("pages").asLong()).isEqualTo(2);
        assertThat(first.path("records").get(0).path("id").asText()).isEqualTo(c.path("id").asText());
        assertThat(success(get(search(h.prefix()) + "&page=2&size=1"), 200).path("records").get(0).path("id").asText())
                .isEqualTo(a.path("id").asText());
        JsonNode beyond = success(get(search(h.prefix()) + "&page=3&size=1"), 200);
        assertThat(beyond.path("records").size()).isZero(); assertThat(beyond.path("total").asLong()).isEqualTo(2);
        JsonNode empty = success(get(search(h.named("missing"))), 200);
        assertThat(empty.path("records").size()).isZero(); assertThat(empty.path("pages").asLong()).isZero();
        assertThat(success(get(PUBLIC + "/" + b.path("id").asText()), 200).path("status").asText()).isEqualTo("INACTIVE");
        assertThat(success(get(search(h.prefix()) + "&destinationType=OTHER"), 200).path("total").asLong()).isEqualTo(1);
        JsonNode hidden = success(h.request("GET", ADMIN + "?keyword=" + h.prefix() + "&status=INACTIVE", admin.token(), null, Map.of()), 200);
        assertThat(hidden.path("total").asLong()).isEqualTo(1);
    }

    @Test void administrationRejectsAnonymousAndOrdinaryUsers() {
        Account user = h.account(false);
        error(h.json("POST", ADMIN, null, body("anon", Map.of())), 401, 40101);
        error(h.request("GET", ADMIN, user.token(), null, Map.of()), 403, 40301);
        error(h.json("POST", ADMIN, user.token(), body("ordinary", Map.of())), 403, 40301);
        error(h.json("PUT", ADMIN + "/1", user.token(), body("ordinary", Map.of())), 403, 40301);
        error(h.json("PATCH", ADMIN + "/1/status", user.token(), Map.of("status", "INACTIVE")), 403, 40301);
    }

    @Test void putClearsNullableFieldsAndStatusPatchIsIdempotent() {
        Account admin = h.account(true);
        JsonNode parent = create(admin, "parent", Map.of());
        JsonNode child = create(admin, "child", Map.of("destinationType","OTHER","parentId", parent.path("id").asText(), "province", "浙江", "city", "杭州",
                "description", "旧简介", "recommendedSeasons", "春", "coverImageUrl", "/uploads/cover.png"));
        Map<String, Object> replacement = full(child);
        for (String key : List.of("parentId", "province", "city", "description", "coverImageUrl", "recommendedSeasons")) replacement.remove(key);
        JsonNode changed = success(put(admin, child, replacement), 200);
        for (String key : List.of("parentId", "province", "city", "description", "coverImageUrl", "recommendedSeasons"))
            assertThat(changed.has(key) && changed.path(key).isNull()).as(key + " is cleared").isTrue();
        assertThat(changed.path("createdAt").asText()).isEqualTo(child.path("createdAt").asText());
        JsonNode inactive = state(admin, changed, "INACTIVE");
        assertThat(state(admin, changed, "INACTIVE")).isEqualTo(inactive);
    }

    @Test void uniqueNamesRemainOccupiedAfterLogicalDeletion() {
        Account admin = h.account(true);
        JsonNode value = create(admin, "unique", Map.of());
        error(h.json("POST", ADMIN, admin.token(), body("unique", Map.of())), 409, 40901);
        h.jdbc().update("UPDATE destinations SET is_deleted=1 WHERE id=? AND name=?", value.path("id").asText(), h.named("unique"));
        error(h.json("POST", ADMIN, admin.token(), body("unique", Map.of())), 409, 40901);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM destinations WHERE name=? AND is_deleted=1", Integer.class, h.named("unique"))).isEqualTo(1);
    }

    @Test void requiredFieldsTypesEnumsAndPublicQueryBoundariesAreStrict() {
        Account admin = h.account(true);
        int index = 0;
        for (Map<String, ?> bad : List.of(Map.of("destinationType", "BAD"), Map.of("sortOrder", -1), Map.of("sortOrder", 1.5),
                Map.of("sortOrder", "1"), Map.of("role", "ADMIN"), Map.of("parentId", 1))) {
            error(h.json("POST", ADMIN, admin.token(), body("invalid" + index++, bad)), 400, 40001);
        }
        for (String field : List.of("name", "destinationType", "country", "status", "sortOrder")) {
            Map<String, Object> bad = body("null" + index++, Map.of()); bad.put(field, null);
            error(h.json("POST", ADMIN, admin.token(), bad), 400, 40001);
        }
        JsonNode value = create(admin, "required", Map.of());
        for (String field : List.of("name", "destinationType", "country", "status", "sortOrder")) {
            Map<String, Object> incomplete = full(value); incomplete.remove(field);
            error(put(admin, value, incomplete), 400, 40001);
        }
        for (String query : List.of("page=0", "page=1.5", "size=0", "size=51", "destinationType=BAD", "parentId=0"))
            error(get(PUBLIC + "?" + query), 400, 40001);
        error(get(search("x".repeat(101))), 400, 40001);
        for (String id : List.of("0", "-1", "x", "9223372036854775808")) error(get(PUBLIC + "/" + id), 400, 40001);
        error(get(PUBLIC + "/9223372036854775807"), 404, 40401);
    }

    @Test void parentCyclesAndInactiveNewReferencesAreRejected() {
        Account admin = h.account(true);
        JsonNode a = create(admin, "a", Map.of("destinationType","OTHER"));
        JsonNode b = create(admin, "b", Map.of("destinationType","OTHER","parentId", a.path("id").asText()));
        JsonNode c = create(admin, "c", Map.of("destinationType","OTHER","parentId", b.path("id").asText()));
        Map<String, Object> bad = full(a); bad.put("parentId", c.path("id").asText()); error(put(admin, a, bad), 400, 40001);
        bad = full(b); bad.put("parentId", b.path("id").asText()); error(put(admin, b, bad), 400, 40001);
        state(admin, a, "INACTIVE");
        Map<String, Object> preserved = full(b); preserved.put("description", "保留历史关联");
        assertThat(success(put(admin, b, preserved), 200).path("parentId").asText()).isEqualTo(a.path("id").asText());
        error(h.json("POST", ADMIN, admin.token(), body("new", Map.of("parentId", a.path("id").asText()))), 400, 40001);
        error(h.json("POST", ADMIN, admin.token(), body("absent", Map.of("parentId", "9223372036854775807"))), 400, 40001);
    }

    @Test void simultaneousMutualParentUpdatesCannotCommitACycle() throws Exception {
        Account admin = h.account(true);
        JsonNode a = create(admin, "a", Map.of("destinationType","OTHER")), b = create(admin, "b", Map.of("destinationType","OTHER"));
        Map<String, Object> aBody = full(a), bBody = full(b);
        aBody.put("parentId", b.path("id").asText()); bBody.put("parentId", a.path("id").asText());
        CountDownLatch start = new CountDownLatch(1);
        List<Result> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Result> one = executor.submit(() -> { start.await(); return put(admin, a, aBody); });
            Future<Result> two = executor.submit(() -> { start.await(); return put(admin, b, bBody); });
            start.countDown();
            results = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        }
        assertThat(results.stream().filter(r -> r.status() == 200).count()).isEqualTo(1);
        for (Result result : results) if (result.status() != 200) {
            assertThat(result.status()).isIn(400, 409);
            assertThat(result.json().path("code").asInt()).isIn(40001, 40902);
        }
        Long aParent = h.jdbc().queryForObject("SELECT parent_id FROM destinations WHERE id=?", Long.class, a.path("id").asText());
        Long bParent = h.jdbc().queryForObject("SELECT parent_id FROM destinations WHERE id=?", Long.class, b.path("id").asText());
        assertThat(Objects.equals(aParent, Long.valueOf(b.path("id").asText()))
                && Objects.equals(bParent, Long.valueOf(a.path("id").asText()))).as("The committed graph is acyclic").isFalse();
    }

    @Test void keywordWildcardsAreLiteralAndCoverPathsAreManaged() {
        Account admin = h.account(true);
        create(admin, "literal%_!", Map.of()); create(admin, "literalXX", Map.of());
        assertThat(success(get(search(h.named("literal%_!"))), 200).path("total").asLong()).isEqualTo(1);
        create(admin, "cover", Map.of("coverImageUrl", "/uploads/cover.png"));
        int index = 0;
        for (String path : List.of("https://example.com/x.png", "/uploads/../x.png", "/uploads/missing.png"))
            error(h.json("POST", ADMIN, admin.token(), body("badCover" + index++, Map.of("coverImageUrl", path))), 400, 40001);
    }
}
