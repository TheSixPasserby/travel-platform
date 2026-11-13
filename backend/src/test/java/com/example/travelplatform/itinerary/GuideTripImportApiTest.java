package com.example.travelplatform.itinerary;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuideTripImportApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    Account a, b;
    String guide, city;
    List<String> spots;
    @BeforeEach void setup() {
        h = new HttpTestSupport(source, port, "m5t"); a = h.account(false); b = h.account(false);
        h.jdbc().update("INSERT INTO destinations(name,destination_type) VALUES (?,'CITY')", h.named("城"));
        city = h.jdbc().queryForObject("SELECT id FROM destinations WHERE name=?", String.class, h.named("城"));
        spots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            h.jdbc().update("INSERT INTO destinations(name,destination_type,parent_id) VALUES (?,'SCENIC_SPOT',?)", h.named("景" + i), city);
            spots.add(h.jdbc().queryForObject("SELECT id FROM destinations WHERE name=?", String.class, h.named("景" + i)));
        }
        guide = success(h.json("POST", "/api/v1/guides", a.token(), Map.of("title", h.named("攻略"), "content", "正文", "status", "PUBLISHED", "destinationId", city, "scenicSpotIds", spots.subList(0, 2))), 201).path("id").asText();
    }
    @AfterEach void cleanup() {
        try {
            if (h.jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='itinerary_guide_imports'", Integer.class) > 0)
                h.jdbc().update("DELETE FROM itinerary_guide_imports WHERE user_id IN (?,?)", a.id(), b.id());
            h.jdbc().update("DELETE FROM itineraries WHERE user_id IN (?,?)", a.id(), b.id());
            h.cleanupGuides(); h.cleanupDestinations();
        } finally { h.cleanupAccounts(); }
    }
    Map<String, Object> newRequest() {
        var body = new HashMap<String, Object>();
        body.put("clientRequestId", UUID.randomUUID().toString()); body.put("guideId", guide);
        body.put("destinationIds", List.copyOf(spots.subList(0, 2)));
        body.put("newItinerary", Map.of("title", h.named("私人计划"), "startDate", "2026-11-20", "endDate", "2026-11-22", "travelDate", "2026-11-21"));
        return body;
    }
    Result post(String token, Map<String, Object> body) { return h.json("POST", "/api/v1/itineraries/guide-imports", token, body); }
    JsonNode saved(Map<String, Object> body) { return success(post(a.token(), body), 200); }
    @Test void createsOneAtomicPrivateTripAndReplaysSameReceiptWithoutDuplicatingItems() {
        var body = newRequest(); var first = saved(body);
        assertThat(first.path("added")).hasSize(2);
        assertThat(saved(body)).isEqualTo(first);
        var trip = success(h.json("GET", "/api/v1/itineraries/" + first.path("itineraryId").asText(), a.token(), null), 200);
        assertThat(trip.path("itemCount").asInt()).isEqualTo(2);
        assertThat(trip.path("days").get(0).path("travelDate").asText()).isEqualTo("2026-11-21");
        assertThat(h.json("GET", "/api/v1/itineraries/" + first.path("itineraryId").asText(), b.token(), null).status()).isEqualTo(403);
        body.put("destinationIds", List.of(spots.getFirst()));
        assertThat(post(a.token(), body).status()).isEqualTo(409);
    }
    @Test void appendsWithDatabaseSerializationAndDedupesAcrossDifferentRequestIds() throws Exception {
        var initial = newRequest(); initial.put("destinationIds", List.of(spots.getFirst()));
        var first = saved(initial);
        var body = newRequest(); body.remove("newItinerary");
        body.put("destinationIds", List.of(spots.get(1)));
        body.put("itineraryId", first.path("itineraryId").asText()); body.put("dayId", first.path("dayId").asText());
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> saved(body));
            var other = new HashMap<>(body); other.put("clientRequestId", UUID.randomUUID().toString());
            var two = executor.submit(() -> saved(other));
            var results = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertThat(results.stream().mapToInt(result -> result.path("added").size()).sum()).isEqualTo(1);
            assertThat(results.stream().mapToInt(result -> result.path("alreadyPresent").size()).sum()).isEqualTo(1);
        } finally { executor.shutdownNow(); }
        assertThat(post(b.token(), body).status()).isEqualTo(403);
        assertThat(post(null, body).status()).isEqualTo(401);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_items WHERE itinerary_day_id=?", Integer.class, first.path("dayId").asText())).isEqualTo(2);
    }
    @Test void unlinkedInactiveHiddenAndInvalidSelectionsFailWithoutPartialRootsOrReceipts() {
        var body = newRequest(); body.put("destinationIds", List.of(spots.getFirst(), spots.get(2)));
        assertThat(post(a.token(), body).status()).isEqualTo(400);
        body.put("destinationIds", List.of(spots.getFirst(), spots.getFirst()));
        assertThat(post(a.token(), body).status()).isEqualTo(400);
        body.put("destinationIds", spots.subList(0, 2));
        h.jdbc().update("UPDATE destinations SET status='INACTIVE' WHERE id=?", spots.get(1));
        assertThat(post(a.token(), body).status()).isEqualTo(400);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=?", guide);
        assertThat(post(a.token(), body).status()).isEqualTo(404);
        h.jdbc().update("UPDATE guides SET status='PUBLISHED' WHERE id=?", guide);
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=?", a.id());
        assertThat(post(b.token(), body).status()).isEqualTo(404);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itineraries WHERE user_id=?", Integer.class, a.id())).isZero();
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_guide_imports WHERE user_id=?", Integer.class, a.id())).isZero();
    }
    @Test void capacityFailureAfterFirstInsertRollsBackAllItemsAndReceipt() {
        var trip = success(h.json("POST", "/api/v1/itineraries", a.token(), Map.of("title", h.named("手动"), "startDate", "2026-11-20", "endDate", "2026-11-22")), 201);
        var day = success(h.json("POST", "/api/v1/itineraries/" + trip.path("id").asText() + "/days", a.token(), Map.of("travelDate", "2026-11-21")), 201);
        for (int i = 0; i < 99; i++) h.jdbc().update("INSERT INTO itinerary_items(itinerary_day_id,place_name,sort_order) VALUES (?,?,?)", day.path("id").asText(), "原手动项目", i);
        var body = newRequest(); body.remove("newItinerary"); body.put("itineraryId", trip.path("id").asText()); body.put("dayId", day.path("id").asText());
        assertThat(post(a.token(), body).status()).isEqualTo(409);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_items WHERE itinerary_day_id=?", Integer.class, day.path("id").asText())).isEqualTo(99);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_guide_imports WHERE user_id=?", Integer.class, a.id())).isZero();
    }
    @Test void committedReceiptRemainsReplayableAfterTheOwnerArchivesOrDeletesTheTarget() {
        var body = newRequest(); var first = saved(body);
        h.jdbc().update("UPDATE itineraries SET status='ARCHIVED' WHERE id=?", first.path("itineraryId").asText());
        assertThat(saved(body)).isEqualTo(first);
        h.jdbc().update("UPDATE itineraries SET is_deleted=1 WHERE id=?", first.path("itineraryId").asText());
        assertThat(saved(body)).isEqualTo(first);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itineraries WHERE user_id=?", Integer.class, a.id())).isEqualTo(1);
    }
}
