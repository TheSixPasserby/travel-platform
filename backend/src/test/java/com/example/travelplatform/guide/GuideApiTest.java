package com.example.travelplatform.guide;

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
import java.nio.file.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuideApiTest {
    private static final Path IMAGES = privateImages();
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;

    private static Path privateImages() {
        try {
            Path root = Files.createTempDirectory("travel-guide-api-").toAbsolutePath().normalize();
            for (String name : List.of("guide.png", "second.png")) {
                ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", root.resolve(name).toFile());
            }
            return root;
        } catch (Exception exception) { throw new IllegalStateException("Cannot create guide image fixture"); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("travel.storage.upload-dir", IMAGES::toString); }
    @BeforeEach void fixtures() { h = new HttpTestSupport(source, port, "m3g"); }
    @AfterEach void cleanup() {
        if (h != null) {
            try { h.cleanupGuides(); }
            finally { try { h.cleanupDestinations(); } finally { h.cleanupAccounts(); } }
        }
    }
    @AfterAll static void cleanupImages() throws Exception {
        assertThat(IMAGES.getFileName().toString()).startsWith("travel-guide-api-");
        assertThat(IMAGES.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())).isTrue();
        try (var paths = Files.walk(IMAGES)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
    }
    private Map<String,Object> body(String suffix, Map<String,?> extras) {
        Map<String,Object> b = new LinkedHashMap<>(); b.put("title",h.named(suffix)); b.put("content","沿溪慢行\n\n山谷的清晨。"); b.putAll(extras); return b;
    }
    private JsonNode create(Account author, String suffix, Map<String,?> extras) {
        return success(h.json("POST","/api/v1/guides",author.token(),body(suffix,extras)),201);
    }
    private String url(JsonNode g) { return "/api/v1/guides/"+g.path("id").asText(); }
    private String ownUrl(JsonNode g) { return "/api/v1/users/me/guides/"+g.path("id").asText(); }
    private Result get(String path, Account a) { return h.json("GET",path,a==null?null:a.token(),null); }
    private JsonNode own(Account a, JsonNode g) { return success(get(ownUrl(g),a),200); }
    private String keyword(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private JsonNode publicPage(String query) { return success(get("/api/v1/guides?keyword="+keyword(h.prefix())+query,null),200); }
    private List<String> ids(JsonNode array) { List<String> out=new ArrayList<>(); array.forEach(x->out.add(x.path("id").asText())); return out; }
    private Map<String,Object> replacement(JsonNode g) {
        Map<String,Object> b=new LinkedHashMap<>();
        for (String key : List.of("title","content","summary","coverImageUrl","season","budgetPerPerson")) {
            b.put(key,g.path(key).isNull()?null:g.path(key).asText());
        }
        b.put("destinationId",g.path("destination").isNull()?null:g.path("destination").path("id").asText());
        b.put("travelDays",g.path("travelDays").isNull()?null:g.path("travelDays").asInt());
        b.put("tagIds",ids(g.path("tags")));
        List<Map<String,Object>> images=new ArrayList<>();
        g.path("images").forEach(image->{Map<String,Object> item=new LinkedHashMap<>();item.put("url",image.path("url").asText());item.put("altText",image.path("altText").isNull()?null:image.path("altText").asText());images.add(item);});
        b.put("images",images); return b;
    }
    private JsonNode destination(Account admin,String suffix) {
        return success(h.json("POST","/api/v1/admin/destinations",admin.token(),Map.of("name",h.named(suffix))),201);
    }
    private JsonNode tags() { return success(get("/api/v1/tags",null),200); }

    @Test void publicReadsAndAnonymousWritesHaveSeparateBoundaries() {
        assertThat(publicPage("").path("total").asLong()).isZero();
        JsonNode tags=tags(); assertThat(tags.isArray()).isTrue(); assertThat(tags.size()).isGreaterThanOrEqualTo(2);
        assertThat(tags.get(0).path("id").isTextual()).isTrue();
        error(h.json("POST","/api/v1/guides",null,body("anonymous",Map.of())),401,40101);
        error(get("/api/v1/users/me/guides",null),401,40101);
        Account a=h.account(false);
        JsonNode profile=success(get("/api/v1/users/"+a.id()+"/profile",null),200);
        assertThat(profile.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","nickname","avatarUrl","bio");
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",a.id(),a.username());
        success(get("/api/v1/users/"+a.id()+"/profile",null),200);
    }

    @Test void draftsArePrivateAndAllAuthorOperationsRejectOtherUsersIncludingAdmin() {
        Account a=h.account(false),b=h.account(false),admin=h.account(true);
        JsonNode g=create(a,"private",Map.of("content",""));
        assertThat(g.path("id").isTextual()).isTrue(); assertThat(g.path("status").asText()).isEqualTo("DRAFT");
        assertThat(g.path("publishedAt").isNull()).isTrue(); assertThat(g.path("author").path("id").asText()).isEqualTo(a.id());
        assertThat(g.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","title","summary","coverImageUrl","author","destination","travelDays","budgetPerPerson","season","tags","status","publishedAt","createdAt","updatedAt","likeCount","favoriteCount","commentCount","content","images");
        success(get(ownUrl(g),a),200); error(get(url(g),null),404,40401);
        for (Account other : List.of(b,admin)) {
            error(get(ownUrl(g),other),403,40301);
            error(h.json("PUT",url(g),other.token(),replacement(g)),403,40301);
            error(h.json("POST",url(g)+"/publish",other.token(),null),403,40301);
            error(h.json("POST",url(g)+"/unpublish",other.token(),null),403,40301);
            error(h.json("DELETE",url(g),other.token(),null),403,40301);
        }
        assertThat(success(get("/api/v1/users/me/guides",a),200).path("records").size()).isEqualTo(1);
    }

    @Test void firstPublicationTimeSurvivesEditingWithdrawalAndRepeatedActions() {
        Account a=h.account(false); JsonNode g=create(a,"lifecycle",Map.of());
        JsonNode published=success(h.json("POST",url(g)+"/publish",a.token(),null),200);
        String time=published.path("publishedAt").asText(); assertThat(time).matches(".*\\.\\d{3}\\+08:00");
        assertThat(success(h.json("POST",url(g)+"/publish",a.token(),null),200).path("publishedAt").asText()).isEqualTo(time);
        Map<String,Object> edit=replacement(published);edit.put("title",h.named("edited"));
        assertThat(success(h.json("PUT",url(g),a.token(),edit),200).path("publishedAt").asText()).isEqualTo(time);
        JsonNode draft=success(h.json("POST",url(g)+"/unpublish",a.token(),null),200);
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");error(get(url(g),null),404,40401);
        assertThat(success(h.json("POST",url(g)+"/unpublish",a.token(),null),200).path("publishedAt").asText()).isEqualTo(time);
        assertThat(success(h.json("POST",url(g)+"/publish",a.token(),null),200).path("publishedAt").asText()).isEqualTo(time);
        success(get(url(g),null),200);
    }

    @Test void hiddenContentCannotBeRestoredByTheAuthorButCanBePrivatelyReadAndDeleted() {
        Account a=h.account(false);JsonNode g=create(a,"hidden",Map.of("status","PUBLISHED"));
        assertThat(h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=? AND author_id=?",g.path("id").asText(),a.id())).isEqualTo(1);
        error(get(url(g),null),404,40401);
        assertThat(own(a,g).path("status").asText()).isEqualTo("HIDDEN");
        error(h.json("PUT",url(g),a.token(),replacement(g)),409,40902);
        for (String action : List.of("publish","unpublish")) error(h.json("POST",url(g)+"/"+action,a.token(),null),409,40902);
        assertThat(success(get("/api/v1/users/me/guides?status=HIDDEN",a),200).path("records").size()).isEqualTo(1);
        assertThat(success(h.json("DELETE",url(g),a.token(),null),200).isNull()).isTrue();
        error(h.json("DELETE",url(g),a.token(),null),404,40401);error(get(ownUrl(g),a),404,40401);
    }

    @Test void publishedPutAndJsonFieldTypesCannotBypassValidation() {
        Account a=h.account(false);JsonNode g=create(a,"strict",Map.of("status","PUBLISHED"));
        Map<String,Object> edit=replacement(g);edit.put("content"," \n\t");
        error(h.json("PUT",url(g),a.token(),edit),400,40001);
        assertThat(own(a,g).path("content")).isEqualTo(g.path("content"));
        edit=replacement(g);edit.put("status","HIDDEN");error(h.json("PUT",url(g),a.token(),edit),400,40001);
        for (Map<String,?> extra : List.of(Map.of("authorId",a.id()),Map.of("budgetPerPerson",0.1),Map.of("travelDays",1.5),Map.of("title",true))) {
            error(h.json("POST","/api/v1/guides",a.token(),body("badtype",extra)),400,40001);
        }
        Map<String,Object> nullStatus=body("nullstatus",Map.of());nullStatus.put("status",null);
        error(h.json("POST","/api/v1/guides",a.token(),nullStatus),400,40001);
        error(h.json("POST","/api/v1/guides",a.token(),body("blank",Map.of("status","PUBLISHED","content"," "))),400,40001);
    }

    @Test void relationReplacementIsAtomicAndNullableFieldsReallyClear() {
        Account a=h.account(false);JsonNode tags=tags();String first=tags.get(0).path("id").asText(),second=tags.get(1).path("id").asText();
        JsonNode g=create(a,"relations",Map.of("status","PUBLISHED","budgetPerPerson","0.1","summary","清晨","coverImageUrl","/uploads/guide.png","tagIds",List.of(second,first),"images",List.of(Map.of("url","/uploads/second.png","altText","溪流"),Map.of("url","/uploads/guide.png"))));
        assertThat(g.path("budgetPerPerson").isTextual()).isTrue();assertThat(g.path("budgetPerPerson").asText()).isEqualTo("0.10");
        assertThat(ids(g.path("tags"))).containsExactly(first,second);
        assertThat(g.path("images").get(0).path("url").asText()).isEqualTo("/uploads/second.png");
        assertThat(g.path("images").get(0).path("sortOrder").asInt()).isZero();
        Map<String,Object> edit=replacement(g);edit.put("title",h.named("rollback"));edit.put("images",List.of(Map.of("url","/uploads/guide.png"),Map.of("url","/uploads/missing.png")));
        error(h.json("PUT",url(g),a.token(),edit),400,40001);assertThat(own(a,g)).isEqualTo(g);
        edit=replacement(g);edit.put("tagIds",List.of("9223372036854775807"));
        error(h.json("PUT",url(g),a.token(),edit),400,40001);assertThat(own(a,g)).isEqualTo(g);
        edit=replacement(g);for(String field:List.of("summary","coverImageUrl","budgetPerPerson","season","travelDays","destinationId"))edit.put(field,null);
        edit.put("tagIds",List.of());edit.put("images",List.of());JsonNode cleared=success(h.json("PUT",url(g),a.token(),edit),200);
        assertThat(cleared.path("summary").isNull()).isTrue();assertThat(cleared.path("budgetPerPerson").isNull()).isTrue();assertThat(cleared.path("coverImageUrl").isNull()).isTrue();
        assertThat(cleared.path("images").isEmpty()).isTrue();assertThat(cleared.path("tags").isEmpty()).isTrue();
        assertThat(Files.exists(IMAGES.resolve("guide.png"))).isTrue();
    }

    @Test void inactiveDestinationsRetainHistoryButCannotBeNewReferences() {
        Account a=h.account(false),admin=h.account(true);JsonNode d=destination(admin,"destination");String id=d.path("id").asText();
        JsonNode g=create(a,"history",Map.of("destinationId",id));
        success(h.json("PATCH","/api/v1/admin/destinations/"+id+"/status",admin.token(),Map.of("status","INACTIVE")),200);
        JsonNode updated=success(h.json("PUT",url(g),a.token(),replacement(g)),200);assertThat(updated.path("destination").path("status").asText()).isEqualTo("INACTIVE");
        success(h.json("POST",url(g)+"/publish",a.token(),null),200);
        assertThat(publicPage("&destinationId="+id).path("total").asLong()).isEqualTo(1);
        error(h.json("POST","/api/v1/guides",a.token(),body("newinactive",Map.of("destinationId",id))),400,40001);
        JsonNode other=create(a,"other",Map.of());Map<String,Object> edit=replacement(other);edit.put("destinationId",id);
        error(h.json("PUT",url(other),a.token(),edit),400,40001);
    }

    @Test void literalSearchFiltersCountsAndStableDatabasePaginationAgree() {
        Account a=h.account(false),b=h.account(false),admin=h.account(true);JsonNode d=destination(admin,"filter");String tag=tags().get(0).path("id").asText();
        JsonNode x=create(a,"literal%_!\\stop",Map.of("status","PUBLISHED","destinationId",d.path("id").asText(),"tagIds",List.of(tag)));
        JsonNode y=create(a,"second",Map.of("status","PUBLISHED","tagIds",List.of(tag)));JsonNode z=create(a,"third",Map.of("status","PUBLISHED"));
        create(a,"draft",Map.of());
        for (JsonNode guide : List.of(x,y,z)) h.jdbc().update("UPDATE guides SET published_at='2026-11-03 08:00:00.000' WHERE id=? AND author_id=?",guide.path("id").asText(),a.id());
        h.jdbc().update("INSERT INTO guide_likes(user_id,guide_id) VALUES (?,?),(?,?),(?,?)",a.id(),x.path("id").asText(),b.id(),x.path("id").asText(),a.id(),y.path("id").asText());
        h.jdbc().update("INSERT INTO guide_favorites(user_id,guide_id) VALUES (?,?)",b.id(),x.path("id").asText());
        h.jdbc().update("INSERT INTO guide_comments(guide_id,user_id,content,status,is_deleted) VALUES (?,?,'可见','NORMAL',0),(?,?,'隐藏','HIDDEN',0),(?,?,'删除','NORMAL',1)",x.path("id").asText(),a.id(),x.path("id").asText(),a.id(),x.path("id").asText(),a.id());
        JsonNode popular=publicPage("&sort=popular&size=1");assertThat(popular.path("total").asLong()).isEqualTo(3);assertThat(popular.path("pages").asLong()).isEqualTo(3);
        JsonNode top=popular.path("records").get(0);assertThat(top.path("id")).isEqualTo(x.path("id"));assertThat(top.path("likeCount").asInt()).isEqualTo(2);assertThat(top.path("favoriteCount").asInt()).isEqualTo(1);assertThat(top.path("commentCount").asInt()).isEqualTo(1);
        assertThat(ids(publicPage("&sort=latest").path("records"))).containsExactly(z.path("id").asText(),y.path("id").asText(),x.path("id").asText());
        JsonNode beyond=publicPage("&page=999&size=1");assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("total").asLong()).isEqualTo(3);assertThat(beyond.path("current").asLong()).isEqualTo(999);
        for(String literal:List.of("%","_","!","\\")) assertThat(success(get("/api/v1/guides?keyword="+keyword(h.prefix()+"literal"+literal),null),200).path("total").asLong()).isEqualTo(literal.equals("%")?1:0);
        assertThat(success(get("/api/v1/guides?keyword="+keyword(h.named("literal%_!\\stop")),null),200).path("total").asLong()).isEqualTo(1);
        assertThat(publicPage("&tagId="+tag).path("total").asLong()).isEqualTo(2);
        assertThat(publicPage("&tagId="+tag+"&destinationId="+d.path("id").asText()).path("total").asLong()).isEqualTo(1);
        assertThat(publicPage("&status=HIDDEN").path("total").asLong()).isEqualTo(3);
        assertThat(success(get("/api/v1/guides?keyword="+keyword(h.prefix()+"%' OR 1=1 --"),null),200).path("total").asLong()).isZero();
    }

    @Test void invalidQueryValuesAreRejectedWithoutSilentlyClampingPages() {
        Account a=h.account(false);
        for(String query:List.of("page=0","page=1.5","size=51","size=0","sort=unknown","destinationId=NaN","tagId=9223372036854775808","page=9223372036854775807&size=50")) error(get("/api/v1/guides?"+query,null),400,40001);
        error(get("/api/v1/users/me/guides?status=UNKNOWN",a),400,40001);
        assertThat(publicPage("&destinationId=9223372036854775807").path("total").asLong()).isZero();
    }

    @Test void deletionIsLogicalAndRetainsPreviouslyUploadedFiles() {
        Account a=h.account(false);JsonNode g=create(a,"delete",Map.of("status","PUBLISHED","coverImageUrl","/uploads/guide.png","images",List.of(Map.of("url","/uploads/guide.png"))));
        success(h.json("DELETE",url(g),a.token(),null),200);
        assertThat(h.jdbc().queryForObject("SELECT is_deleted FROM guides WHERE id=? AND author_id=?",Integer.class,g.path("id").asText(),a.id())).isEqualTo(1);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_images WHERE guide_id=?",Integer.class,g.path("id").asText())).isEqualTo(1);
        error(get(url(g),null),404,40401);error(get(ownUrl(g),a),404,40401);assertThat(Files.exists(IMAGES.resolve("guide.png"))).isTrue();
    }

    @Test void publishingRevalidatesStoredImagePathsAndRollsBackOnMissingFiles() throws Exception {
        Account a=h.account(false);Path temporary=IMAGES.resolve(h.named("publish.png"));
        ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",temporary.toFile());
        JsonNode g;
        try { g=create(a,"missingimage",Map.of("coverImageUrl","/uploads/"+temporary.getFileName())); }
        finally { Files.deleteIfExists(temporary); }
        error(h.json("POST",url(g)+"/publish",a.token(),null),400,40001);
        JsonNode unchanged=own(a,g);assertThat(unchanged.path("status").asText()).isEqualTo("DRAFT");assertThat(unchanged.path("publishedAt").isNull()).isTrue();
    }

    @Test void simultaneousPublishAndBlankEditNeverProducePublishedBlankContent() throws Exception {
        Account a=h.account(false);JsonNode g=create(a,"race",Map.of());Map<String,Object> blank=replacement(g);blank.put("content"," ");
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Result> publish=executor.submit(()->{start.await();return h.json("POST",url(g)+"/publish",a.token(),null);});
            Future<Result> edit=executor.submit(()->{start.await();return h.json("PUT",url(g),a.token(),blank);});start.countDown();
            List<Integer> statuses=List.of(publish.get(25,TimeUnit.SECONDS).status(),edit.get(25,TimeUnit.SECONDS).status());assertThat(statuses).containsExactlyInAnyOrder(200,400);
            JsonNode finalValue=own(a,g);assertThat(finalValue.path("status").asText().equals("PUBLISHED")&&finalValue.path("content").asText().isBlank()).isFalse();
        } finally { executor.shutdownNow(); }
    }

    @Test void destinationCurrentReadSeesDisableCommittedWhileCreationWasWaiting() throws Exception {
        Account a=h.account(false),admin=h.account(true);JsonNode d=destination(admin,"locked");String id=d.path("id").asText();ExecutorService executor=Executors.newSingleThreadExecutor();
        try (var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement=connection.prepareStatement("UPDATE destinations SET status='INACTIVE' WHERE id=?")) { statement.setLong(1,Long.parseLong(id));assertThat(statement.executeUpdate()).isEqualTo(1); }
            try {
                Future<Result> result=executor.submit(()->h.json("POST","/api/v1/guides",a.token(),body("waiting",Map.of("destinationId",id))));
                assertThatThrownBy(()->result.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                connection.commit();error(result.get(25,TimeUnit.SECONDS),400,40001);
                assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guides WHERE author_id=?",Integer.class,a.id())).isZero();
            } finally { connection.rollback(); }
        } finally { executor.shutdownNow(); }
    }
}
