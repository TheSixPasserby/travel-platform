package com.example.travelplatform.interaction;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.example.travelplatform.testsupport.HttpTestSupport.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InteractionApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m4i");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupGuides();}finally{h.cleanupAccounts();}}}
    private JsonNode guide(Account a,String suffix){return success(h.json("POST","/api/v1/guides",a.token(),Map.of("title",h.named(suffix),"content","互动验收攻略","status","PUBLISHED")),201);}
    private String base(JsonNode guide){return "/api/v1/guides/"+guide.path("id").asText();}
    private Result request(String method,String path,Account a){return h.json(method,path,a==null?null:a.token(),null);}
    private JsonNode state(Account a,JsonNode g){return success(request("GET",base(g)+"/interaction",a),200);}
    private JsonNode action(String method,String type,Account a,JsonNode g){return success(request(method,base(g)+"/"+type,a),200);}
    private JsonNode favorites(Account a,String query){return success(request("GET","/api/v1/users/me/favorites?keyword="+URLEncoder.encode(h.prefix(),StandardCharsets.UTF_8)+query,a),200);}

    @Test void anonymousAndDisabledUsersCannotUsePrivateInteractionOrFavorites() {
        Account a=h.account(false);JsonNode g=guide(a,"auth");
        error(request("GET",base(g)+"/interaction",null),401,40101);
        error(request("GET","/api/v1/users/me/favorites",null),401,40101);
        for(String type:List.of("like","favorite"))for(String method:List.of("PUT","DELETE"))error(request(method,base(g)+"/"+type,null),401,40101);
        JsonNode initial=state(a,g);
        assertThat(initial.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("guideId","liked","favorited","likeCount","favoriteCount","commentCount");
        assertThat(initial.path("guideId").isTextual()).isTrue();assertThat(initial.path("liked").isBoolean()).isTrue();assertThat(initial.path("likeCount").isIntegralNumber()).isTrue();
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",a.id(),a.username());
        error(request("GET",base(g)+"/interaction",a),401,40101);error(request("PUT",base(g)+"/favorite",a),401,40101);
    }

    @Test void duplicatePutAndDeleteAreIdempotentWithoutResettingRelationshipTime() {
        Account a=h.account(false);JsonNode g=guide(a,"idempotent");String id=g.path("id").asText();
        for(String type:List.of("like","favorite")) {
            String table=type.equals("like")?"guide_likes":"guide_favorites";
            String flag=type.equals("like")?"liked":"favorited";String count=type.equals("like")?"likeCount":"favoriteCount";
            assertThat(action("PUT",type,a,g).path(flag).asBoolean()).isTrue();
            h.jdbc().update("UPDATE "+table+" SET created_at=DATE_SUB(created_at,INTERVAL 1 DAY) WHERE user_id=? AND guide_id=?",a.id(),id);
            LocalDateTime time=h.jdbc().queryForObject("SELECT created_at FROM "+table+" WHERE user_id=? AND guide_id=?",LocalDateTime.class,a.id(),id);
            for(int repeat=0;repeat<3;repeat++){JsonNode current=action("PUT",type,a,g);assertThat(current.path(flag).asBoolean()).isTrue();assertThat(current.path(count).asLong()).isEqualTo(1);}
            assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=? AND guide_id=?",Integer.class,a.id(),id)).isEqualTo(1);
            assertThat(h.jdbc().queryForObject("SELECT created_at FROM "+table+" WHERE user_id=? AND guide_id=?",LocalDateTime.class,a.id(),id)).isEqualTo(time);
            for(int repeat=0;repeat<2;repeat++){JsonNode current=action("DELETE",type,a,g);assertThat(current.path(flag).asBoolean()).isFalse();assertThat(current.path(count).asLong()).isZero();}
            assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=? AND guide_id=?",Integer.class,a.id(),id)).isZero();
        }
    }

    @Test void usersHaveIndependentFlagsAndSharedCountsAreReal() {
        Account a=h.account(false),b=h.account(false);JsonNode g=guide(a,"independent");
        action("PUT","like",a,g);action("PUT","favorite",a,g);action("PUT","like",b,g);
        assertThat(state(a,g).path("liked").asBoolean()).isTrue();assertThat(state(a,g).path("favorited").asBoolean()).isTrue();
        assertThat(state(b,g).path("liked").asBoolean()).isTrue();assertThat(state(b,g).path("favorited").asBoolean()).isFalse();
        assertThat(state(b,g).path("likeCount").asLong()).isEqualTo(2);
        action("DELETE","like",a,g);action("PUT","favorite",b,g);action("DELETE","favorite",a,g);
        JsonNode publicGuide=success(request("GET",base(g),null),200);
        assertThat(publicGuide.path("likeCount").asLong()).isEqualTo(1);assertThat(publicGuide.path("favoriteCount").asLong()).isEqualTo(1);
        assertThat(favorites(a,"&userId="+b.id()).path("total").asLong()).isZero();assertThat(favorites(b,"").path("total").asLong()).isEqualTo(1);
        error(request("GET","/api/v1/users/"+b.id()+"/favorites",a),404,40401);
    }

    @Test void hiddenWithdrawnAndDeletedGuidesDoNotExposeOrAcceptInteractionsButKeepRows() {
        Account a=h.account(false);JsonNode g=guide(a,"visibility");String id=g.path("id").asText();
        action("PUT","like",a,g);action("PUT","favorite",a,g);
        success(request("POST",base(g)+"/unpublish",a),200);
        for(String path:List.of("interaction","like","favorite"))error(request(path.equals("interaction")?"GET":"PUT",base(g)+"/"+path,a),404,40401);
        error(request("DELETE",base(g)+"/favorite",a),404,40401);assertThat(favorites(a,"").path("total").asLong()).isZero();
        success(request("POST",base(g)+"/publish",a),200);assertThat(state(a,g).path("favorited").asBoolean()).isTrue();
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=? AND author_id=?",id,a.id());
        error(request("GET",base(g)+"/interaction",a),404,40401);assertThat(favorites(a,"").path("total").asLong()).isZero();
        h.jdbc().update("UPDATE guides SET status='PUBLISHED' WHERE id=? AND author_id=?",id,a.id());
        assertThat(favorites(a,"").path("total").asLong()).isEqualTo(1);assertThat(state(a,g).path("liked").asBoolean()).isTrue();
        success(request("DELETE",base(g),a),200);error(request("PUT",base(g)+"/like",a),404,40401);assertThat(favorites(a,"").path("total").asLong()).isZero();
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_likes WHERE user_id=? AND guide_id=?",Integer.class,a.id(),id)).isEqualTo(1);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_favorites WHERE user_id=? AND guide_id=?",Integer.class,a.id(),id)).isEqualTo(1);
    }

    @Test void concurrentRepeatedLikeAndFavoriteStillCreateOnlyOnePairEach() throws Exception {
        Account a=h.account(false);JsonNode g=guide(a,"concurrent");ExecutorService executor=Executors.newFixedThreadPool(8);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Result>> jobs=new ArrayList<>();
            for(int i=0;i<12;i++){String type=i%2==0?"like":"favorite";jobs.add(executor.submit(()->{start.await();return request("PUT",base(g)+"/"+type,a);}));}
            start.countDown();for(Future<Result> job:jobs)success(job.get(25,TimeUnit.SECONDS),200);
            JsonNode current=state(a,g);assertThat(current.path("likeCount").asLong()).isEqualTo(1);assertThat(current.path("favoriteCount").asLong()).isEqualTo(1);
            for(String table:List.of("guide_likes","guide_favorites"))assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=? AND guide_id=?",Integer.class,a.id(),g.path("id").asText())).isEqualTo(1);
        } finally {executor.shutdownNow();}
    }

    @Test void favoritePaginationUsesStableSavedOrderLiteralKeywordAndPublicProjection() {
        Account a=h.account(false),b=h.account(false);JsonNode x=guide(b,"literal%_!\\"),y=guide(a,"second"),z=guide(b,"third");
        for(JsonNode g:List.of(x,y,z))action("PUT","favorite",a,g);
        action("PUT","favorite",b,x);
        h.jdbc().update("UPDATE guide_favorites SET created_at='2026-11-01 00:00:00.000' WHERE user_id=?",a.id());
        JsonNode first=favorites(a,"&size=1");assertThat(first.path("total").asLong()).isEqualTo(3);assertThat(first.path("pages").asLong()).isEqualTo(3);assertThat(first.path("records").get(0).path("id")).isEqualTo(z.path("id"));
        assertThat(favorites(a,"&page=2&size=1").path("records").get(0).path("id")).isEqualTo(y.path("id"));
        JsonNode beyond=favorites(a,"&page=999&size=1");assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("total").asLong()).isEqualTo(3);assertThat(beyond.path("current").asLong()).isEqualTo(999);
        String literal=URLEncoder.encode(h.named("literal%_!\\"),StandardCharsets.UTF_8);
        JsonNode filtered=success(request("GET","/api/v1/users/me/favorites?keyword="+literal,a),200);assertThat(filtered.path("total").asLong()).isEqualTo(1);
        JsonNode summary=filtered.path("records").get(0);assertThat(summary.path("id").isTextual()).isTrue();assertThat(summary.path("author").path("id").isTextual()).isTrue();assertThat(summary.has("content")).isFalse();assertThat(summary.path("author").has("username")).isFalse();
        for(String query:List.of("page=0","size=51","page=9223372036854775807&size=50"))error(request("GET","/api/v1/users/me/favorites?"+query,a),400,40001);
        error(request("PUT","/api/v1/guides/0/like",a),400,40001);
    }

    @Test void interactionWaitingOnGuideLockSeesTheCommittedWithdrawal() throws Exception {
        Account a=h.account(false);JsonNode g=guide(a,"linearized");ExecutorService executor=Executors.newSingleThreadExecutor();
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("UPDATE guides SET status='DRAFT' WHERE id=? AND author_id=?")){statement.setLong(1,Long.parseLong(g.path("id").asText()));statement.setLong(2,Long.parseLong(a.id()));assertThat(statement.executeUpdate()).isEqualTo(1);}
            try {
                Future<Result> job=executor.submit(()->request("PUT",base(g)+"/like",a));
                assertThatThrownBy(()->job.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                connection.commit();error(job.get(25,TimeUnit.SECONDS),404,40401);
                assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_likes WHERE guide_id=?",Integer.class,g.path("id").asText())).isZero();
            } finally {connection.rollback();}
        } finally {executor.shutdownNow();}
    }
}
