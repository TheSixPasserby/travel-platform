package com.example.travelplatform.guide;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class M16SearchApiTest {
    static TimeZone originalZone;
    @BeforeAll static void utcJvm() { originalZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("UTC")); }
    @AfterAll static void restoreZone() { TimeZone.setDefault(originalZone); }
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup() { h = new HttpTestSupport(source, port, "m3g"); }
    @AfterEach void cleanup() {
        try {
            h.cleanupGuides();
            h.cleanupDestinations();
            h.jdbc().update("DELETE FROM tags WHERE name LIKE ?", h.prefix() + "%");
        } finally { h.cleanupAccounts(); }
    }
    String query(String keyword) { return "/api/v1/search?keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8); }
    JsonNode get(String path) { return success(h.json("GET", path, null, null), 200); }
    String guide(Account author, String status, String title) {
        return success(h.json("POST", "/api/v1/guides", author.token(), Map.of("title", title, "content", "旅行记录", "status", status)), 201).path("id").asText();
    }
    List<String> keys(JsonNode page) {
        return page.path("records").valueStream().map(r -> r.path("kind").asText() + ":" + r.path("id").asText()).toList();
    }
    @Test void anonymousSearchCombinesFivePublicKindsAndInternalLinksWithSafeFields() {
        var author = h.account(false);
        String title = h.named("成都");
        h.jdbc().update("UPDATE users SET nickname=? WHERE id=?", title, author.id());
        h.jdbc().update("INSERT INTO destinations(name,destination_type) VALUES (?,'CITY')", title);
        long city = h.jdbc().queryForObject("SELECT id FROM destinations WHERE name=?", Long.class, title);
        h.jdbc().update("INSERT INTO destinations(name,destination_type,parent_id) VALUES (?,'SCENIC_SPOT',?)", title + "公园", city);
        h.jdbc().update("INSERT INTO tags(name) VALUES (?)", title);
        long tag = h.jdbc().queryForObject("SELECT id FROM tags WHERE name=?", Long.class, title);
        String published = guide(author, "PUBLISHED", title);
        String draft = guide(author, "DRAFT", title + "私密");
        h.jdbc().update("INSERT INTO guide_tags(guide_id,tag_id) VALUES (?,?)", published, tag);
        var results = get(query(title));
        assertThat(results.path("records").valueStream().map(r -> r.path("kind").asText()).toList())
                .contains("GUIDE", "CITY", "SCENIC_SPOT", "CREATOR", "TAG");
        assertThat(keys(results)).contains("GUIDE:" + published).doesNotContain("GUIDE:" + draft);
        for (var row : results.path("records")) {
            assertThat(row.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("kind", "id", "title", "subtitle", "coverImageUrl", "username", "href");
            assertThat(row.path("href").asText()).startsWith("/").doesNotStartWith("//");
            if (!row.path("kind").asText().equals("CREATOR")) assertThat(row.path("username").isNull()).isTrue();
        }
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=?", author.id());
        assertThat(keys(get(query(title)))).doesNotContain("GUIDE:" + published, "CREATOR:" + author.id(), "TAG:" + tag);
        h.jdbc().update("UPDATE destinations SET status='INACTIVE' WHERE id=?", city);
        assertThat(keys(get(query(title)))).doesNotContain("CITY:" + city);
        assertThat(get(query(title)).path("records")).isEmpty();
    }
    @Test void cursorPaginationIsStableBindsFiltersAndRechecksDeletedRows() {
        var author = h.account(false);
        var ids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) ids.add(guide(author, "PUBLISHED", h.named("same")));
        h.jdbc().update("UPDATE guides SET published_at='2026-01-01 00:00:00.000' WHERE author_id=?", author.id());
        String base = query(h.named("same")) + "&kind=GUIDE&size=2&sort=latest";
        var first = get(base);
        assertThat(keys(first)).hasSize(2);
        String cursor = first.path("nextCursor").asText();
        var second = get(base + "&cursor=" + cursor);
        assertThat(keys(second)).hasSize(2).doesNotContainAnyElementsOf(keys(first));
        var last = get(base + "&cursor=" + second.path("nextCursor").asText());
        assertThat(keys(last)).hasSize(1);
        assertThat(last.path("hasMore").asBoolean()).isFalse();
        assertThat(h.json("GET", base.replace("latest", "relevance") + "&cursor=" + cursor, null, null).status()).isEqualTo(400);
        assertThat(h.json("GET", base.replace("GUIDE", "CITY") + "&cursor=" + cursor, null, null).status()).isEqualTo(400);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE author_id=?", author.id());
        assertThat(get(base + "&cursor=" + cursor).path("records")).isEmpty();
    }
    @Test void literalWildcardUnicodeEmptyAndInvalidInputsAreHandled() {
        var author = h.account(false);
        String literal = h.named("百分%_!🌄");
        String id = guide(author, "PUBLISHED", literal);
        assertThat(keys(get(query(literal)))).contains("GUIDE:" + id);
        assertThat(get(query(h.named("no-match"))).path("records")).isEmpty();
        assertThat(get("/api/v1/search").path("records")).isEmpty();
        for (String suffix : List.of("size=0", "size=51", "kind=PRIVATE", "sort=random", "cursor=garbage"))
            assertThat(h.json("GET", query(literal) + "&" + suffix, null, null).status()).isEqualTo(400);
        assertThat(h.json("GET", query("长".repeat(101)), null, null).status()).isEqualTo(400);
        assertThat(keys(get(query(literal) + "&kind=GUIDE"))).containsExactly("GUIDE:" + id);
        assertThat(keys(get(query(literal) + "&kind=&sort=&cursor="))).contains("GUIDE:" + id);
        assertThat(h.json("POST", "/api/v1/search", null, Map.of()).status()).isEqualTo(405);
    }
}
