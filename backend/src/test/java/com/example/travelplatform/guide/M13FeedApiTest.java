package com.example.travelplatform.guide;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class M13FeedApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup() { h=new HttpTestSupport(source,port,"m3g"); }
    @AfterEach void cleanup() { try { h.cleanupGuides(); } finally { h.cleanupAccounts(); } }

    String guide(HttpTestSupport.Account author,String status,String suffix) {
        return success(h.json("POST","/api/v1/guides",author.token(),Map.of("title",h.named(suffix),"content","山水间旅行记录","status",status)),201).path("id").asText();
    }
    JsonNode get(String path) { return success(h.json("GET",path,null,null),200); }
    List<String> ids(JsonNode page) {
        var ids=new ArrayList<String>();
        page.path("records").forEach(row->ids.add(row.path("id").asText()));
        return ids;
    }

    @Test void cursorAppendsTwelveThenEightWithTiesAndConcurrentInsertWithoutPrivateRows() {
        var author=h.account(false);
        String base="/api/v1/users/by-username/"+author.username()+"/guides/feed?size=12";
        var expected=new ArrayList<String>();
        for(int i=0;i<20;i++) expected.add(guide(author,"PUBLISHED","feed"+i));
        guide(author,"DRAFT","private");
        h.jdbc().update("UPDATE guides SET published_at='2026-01-01 00:00:00.000' WHERE author_id=? AND status='PUBLISHED'",author.id());
        expected.sort(Comparator.comparingLong((String id)->Long.parseLong(id)).reversed());
        var first=get(base);
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0,12));
        assertThat(first.path("hasMore").asBoolean()).isTrue();
        String newer=guide(author,"PUBLISHED","newer");
        h.jdbc().update("UPDATE guides SET published_at='2026-01-01 00:00:00.000' WHERE id=?",newer);
        var second=get(base+"&cursor="+first.path("nextCursor").asText());
        assertThat(ids(second)).containsExactlyElementsOf(expected.subList(12,20));
        assertThat(second.path("hasMore").asBoolean()).isFalse();
        assertThat(second.path("nextCursor").isNull()).isTrue();
        assertThat(ids(get(base))).contains(newer);
    }

    @Test void cursorBindsQueriesRejectsMalformedInputAndAlwaysRechecksVisibility() {
        var author=h.account(false);var other=h.account(false);
        var firstId=guide(author,"PUBLISHED","a");var hidden=guide(author,"PUBLISHED","b");
        String base="/api/v1/guides/feed?size=1&keyword="+h.named("");
        var first=get(base);String cursor=first.path("nextCursor").asText();
        assertThat(cursor).isNotBlank().isNotEqualTo("null");
        assertThat(h.json("GET",base+"&sort=popular&cursor="+cursor,null,null).status()).isEqualTo(400);
        assertThat(h.json("GET","/api/v1/users/by-username/"+other.username()+"/guides/feed?cursor="+cursor,null,null).status()).isEqualTo(400);
        for(String query:List.of("size=0","size=101","sort=random","cursor=garbage"))
            assertThat(h.json("GET","/api/v1/guides/feed?"+query,null,null).status()).isEqualTo(400);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id IN (?,?)",firstId,hidden);
        assertThat(ids(get(base+"&cursor="+cursor))).isEmpty();
    }

    @Test void popularityUsesSnapshotAndProfileAggregatesOnlyPublicGuides() {
        var author=h.account(false);var reader=h.account(false);
        String publicId=guide(author,"PUBLISHED","popular");String secondId=guide(author,"PUBLISHED","second");
        String privateId=guide(author,"DRAFT","private");
        for(String id:List.of(publicId,privateId)) {
            h.jdbc().update("INSERT INTO guide_likes(user_id,guide_id,created_at) VALUES (?,?,NOW(3)-INTERVAL 1 DAY)",reader.id(),id);
            h.jdbc().update("INSERT INTO guide_favorites(user_id,guide_id) VALUES (?,?)",reader.id(),id);
        }
        String base="/api/v1/guides/feed?sort=popular&size=1&keyword="+h.named("");
        var first=get(base);
        assertThat(ids(first)).containsExactly(publicId);
        h.jdbc().update("INSERT INTO guide_likes(user_id,guide_id,created_at) VALUES (?,?,NOW(3)+INTERVAL 1 SECOND)",reader.id(),secondId);
        assertThat(ids(get(base+"&cursor="+first.path("nextCursor").asText()))).containsExactly(secondId);
        var profile=get("/api/v1/users/by-username/"+author.username());
        assertThat(profile.path("publishedGuideCount").asLong()).isEqualTo(2);
        assertThat(profile.path("likeCount").asLong()).isEqualTo(2);
        assertThat(profile.path("favoriteCount").asLong()).isEqualTo(1);
        assertThat(profile.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","username","nickname","avatarUrl","bio","publishedGuideCount","likeCount","favoriteCount");
    }
}
