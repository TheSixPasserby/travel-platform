package com.example.travelplatform.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP fixtures. Codex alone owns this shared helper; feature tests consume it read-only. */
public final class HttpTestSupport {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final String base;
    private final String prefix;
    private final AtomicInteger sequence = new AtomicInteger();

    public HttpTestSupport(DataSource source, int port, String namespace) {
        if (!namespace.matches("m2[di]|m3g|m4i|m5t|m6a")) throw new IllegalArgumentException("Invalid test namespace");
        this.jdbc = new JdbcTemplate(source);
        this.base = "http://127.0.0.1:" + port;
        this.prefix = namespace + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        assertIsolatedDatabase();
    }

    public String prefix() { return prefix; }
    public String named(String suffix) { return prefix + suffix; }
    public JdbcTemplate jdbc() { return jdbc; }

    public void assertIsolatedDatabase() {
        String test = System.getenv("DB_TEST_NAME");
        String development = System.getenv("DB_NAME");
        assertThat(test != null && !test.isBlank() && test.endsWith("_test"))
                .as("Explicit isolated test database is required").isTrue();
        assertThat(development != null && !development.isBlank() && !development.equalsIgnoreCase(test))
                .as("Development and test databases must be distinct").isTrue();
        assertThat(test.equals(jdbc.queryForObject("SELECT DATABASE()", String.class)))
                .as("Actual database must match the isolated test database").isTrue();
    }

    public record Result(int status, byte[] bytes, HttpHeaders headers) {
        public JsonNode json() {
            try { return JSON.readTree(bytes); }
            catch (Exception exception) { throw new AssertionError("Response is not valid JSON (status=" + status + ")"); }
        }
        public String header(String name) { return headers.firstValue(name).orElse(null); }
        @Override public String toString() { return "Result[status=" + status + ", bytes=" + bytes.length + "]"; }
    }

    public record Account(String id, String username, String token) {
        @Override public String toString() { return "Account[id=" + id + "]"; }
    }

    public Result request(String method, String path, String token, String body, Map<String, String> headers) {
        return requestBytes(method, path, token, body == null ? null : body.getBytes(java.nio.charset.StandardCharsets.UTF_8), headers);
    }

    /** Path is absolute within this test server, including /api/v1 for API calls. */
    public Result requestBytes(String method, String path, String token, byte[] body, Map<String, String> headers) {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IllegalArgumentException("Local test path required");
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("satoken", token);
        if (body != null && headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
            builder.header("Content-Type", "application/json");
        }
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Result(response.statusCode(), response.body(), response.headers());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("HTTP fixture interrupted");
        } catch (Exception exception) {
            StringBuilder types = new StringBuilder();
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (!types.isEmpty()) types.append('/');
                types.append(cause.getClass().getSimpleName());
            }
            throw new AssertionError("HTTP fixture request failed [" + types + "]");
        }
    }

    public Result json(String method, String path, String token, Object body) {
        return request(method, path, token, body == null ? null : encode(body), Map.of());
    }

    public static String encode(Object body) {
        try { return JSON.writeValueAsString(body); }
        catch (Exception exception) { throw new AssertionError("Cannot encode fixture JSON"); }
    }

    public static JsonNode success(Result result, int status) {
        assertThat(result.status()).as("HTTP status").isEqualTo(status);
        JsonNode body = result.json();
        assertThat(body.path("code").isIntegralNumber() && body.path("code").asInt() == 0).isTrue();
        assertThat(body.path("message").asText()).isEqualTo("success");
        assertThat(body.has("data")).isTrue();
        return body.get("data");
    }

    public static void error(Result result, int status, int code) {
        assertThat(result.status()).as("HTTP status").isEqualTo(status);
        assertThat(result.json().path("code").isIntegralNumber()).isTrue();
        assertThat(result.json().path("code").asInt()).isEqualTo(code);
    }

    public Account account(boolean administrator) {
        assertIsolatedDatabase();
        String username = named("u" + sequence.incrementAndGet());
        String password = UUID.randomUUID() + "Aa!";
        JsonNode registered = success(json("POST", "/api/v1/auth/register", null,
                Map.of("username", username, "password", password)), 201);
        String id = registered.path("id").asText();
        if (administrator) {
            int changed = jdbc.update("UPDATE users SET role='ADMIN' WHERE id=? AND username=? AND role='USER' AND is_deleted=0", id, username);
            assertThat(changed).as("Promote only this fixture account").isEqualTo(1);
        }
        JsonNode logged = success(json("POST", "/api/v1/auth/login", null,
                Map.of("username", username, "password", password)), 200);
        String token = logged.path("tokenValue").asText();
        assertThat(!token.isBlank()).as("Fixture login succeeded").isTrue();
        return new Account(id, username, token);
    }

    public void cleanupDestinations() {
        assertIsolatedDatabase();
        assertSafePrefix();
        jdbc.update("UPDATE destinations SET parent_id=NULL WHERE LEFT(name,?)=?", prefix.length(), prefix);
        jdbc.update("DELETE FROM destinations WHERE LEFT(name,?)=?", prefix.length(), prefix);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM destinations WHERE LEFT(name,?)=?", Integer.class,
                prefix.length(), prefix)).isZero();
    }

    public void cleanupAccounts() {
        assertIsolatedDatabase();
        assertSafePrefix();
        jdbc.update("DELETE FROM users WHERE LEFT(username,?)=?", prefix.length(), prefix);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE LEFT(username,?)=?", Integer.class,
                prefix.length(), prefix)).isZero();
    }

    public void cleanupGuides() {
        assertIsolatedDatabase();
        assertSafePrefix();
        String own = "SELECT id FROM guides WHERE author_id IN (SELECT id FROM users WHERE LEFT(username,?)=?)";
        for (String table : new String[]{"guide_images", "guide_tags", "guide_likes", "guide_favorites", "guide_comments"}) {
            // Table names are fixed literals, never supplied by a request or caller.
            jdbc.update("DELETE FROM " + table + " WHERE guide_id IN (" + own + ")", prefix.length(), prefix);
        }
        jdbc.update("DELETE FROM guides WHERE author_id IN (SELECT id FROM users WHERE LEFT(username,?)=?)", prefix.length(), prefix);
    }

    private void assertSafePrefix() {
        assertThat(prefix).matches("(?:m2[di]|m3g|m4i|m5t|m6a)[a-f0-9]{16}");
    }
}
