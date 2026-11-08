package com.example.travelplatform.administration;

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
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdministrationApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m6a");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupGuides();}finally{try{h.cleanupDestinations();}finally{h.cleanupAccounts();}}}}
    private Result call(String method,String path,Account a,Object body){return h.json(method,path,a==null?null:a.token(),body);}
    private JsonNode get(String path,Account a){return success(call("GET",path,a,null),200);}
    private JsonNode patch(String kind,String id,String status,Account admin){return success(call("PATCH",path(kind,id),admin,Map.of("status",status)),200);}
    private String path(String kind,String id){return "/api/v1/admin/"+kind+"/"+id+"/status";}
    private String guidePath(JsonNode g){return "/api/v1/guides/"+g.path("id").asText();}
    private String encoded(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private JsonNode page(String kind,Account admin,String extra){return get("/api/v1/admin/"+kind+"?keyword="+encoded(h.prefix())+extra,admin);}
    private JsonNode guide(Account a,String suffix,String status){return success(call("POST","/api/v1/guides",a,Map.of("title",h.named(suffix),"content","治理验收内容","status",status,"tagIds",List.of("1"))),201);}
    private JsonNode comment(Account a,JsonNode guide,String suffix){return success(call("POST",guidePath(guide)+"/comments",a,Map.of("content",h.named(suffix))),201);}
    private JsonNode userRow(Account admin,Account target){for(JsonNode row:page("users",admin,"&size=50").path("records"))if(row.path("id").asText().equals(target.id()))return row;throw new AssertionError("Fixture user row missing");}

    @Test void allModerationEndpointsRequireCurrentAdministrator(){
        Account admin=h.account(true),user=h.account(false);JsonNode g=guide(user,"auth","PUBLISHED"),c=comment(user,g,"auth");
        for(Account actor:new Account[]{null,user}){
            int status=actor==null?401:403,code=actor==null?40101:40301;
            for(String kind:List.of("users","guides","comments"))error(call("GET","/api/v1/admin/"+kind,actor,null),status,code);
            error(call("PATCH",path("users",user.id()),actor,Map.of("status","DISABLED")),status,code);
            error(call("PATCH",path("guides",g.path("id").asText()),actor,Map.of("status","HIDDEN")),status,code);
            error(call("PATCH",path("comments",c.path("id").asText()),actor,Map.of("status","HIDDEN")),status,code);
        }
        h.jdbc().update("UPDATE users SET role='USER' WHERE id=? AND username=?",admin.id(),admin.username());
        error(call("GET","/api/v1/admin/users",admin,null),403,40301);
        h.jdbc().update("UPDATE users SET role='ADMIN',status='DISABLED' WHERE id=? AND username=?",admin.id(),admin.username());
        error(call("GET","/api/v1/admin/users",admin,null),401,40101);
    }
    @Test void userListHasSafeExactProjectionAndStablePrivateFiltering(){
        Account admin=h.account(true),a=h.account(false),b=h.account(false);
        success(call("PATCH","/api/v1/users/me",a,Map.of("nickname",h.named("literal%_!\\"))),200);
        h.jdbc().update("UPDATE users SET created_at='2026-11-01 00:00:00.000' WHERE LEFT(username,?)=?",h.prefix().length(),h.prefix());
        JsonNode first=page("users",admin,"&size=1");assertThat(first.path("total").asInt()).isEqualTo(3);assertThat(first.path("records").get(0).path("id").asText()).isEqualTo(b.id());
        JsonNode second=page("users",admin,"&size=1&page=2").path("records").get(0);assertThat(second.path("id").asText()).isEqualTo(a.id());
        assertThat(second.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","username","nickname","avatarUrl","role","status","createdAt","updatedAt");assertThat(second.path("id").isTextual()).isTrue();assertThat(second.path("updatedAt").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertThat(page("users",admin,"&role=ADMIN").path("total").asInt()).isEqualTo(1);
        JsonNode beyond=page("users",admin,"&size=1&page=999");assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("current").asInt()).isEqualTo(999);assertThat(beyond.path("total").asInt()).isEqualTo(3);
        assertThat(get("/api/v1/admin/users?keyword="+encoded(h.named("literal%_!\\")),admin).path("total").asInt()).isEqualTo(1);
    }
    @Test void strictStatusBodiesAndQueriesCannotChangeRolesOrInternalFields(){
        Account admin=h.account(true),user=h.account(false);JsonNode g=guide(user,"strict","PUBLISHED"),c=comment(user,g,"strict");
        for(String endpoint:List.of(path("users",user.id()),path("guides",g.path("id").asText()),path("comments",c.path("id").asText()))){
            for(Object body:List.of(Map.of(),Map.of("status",1),Map.of("status","UNKNOWN"),Map.of("status","HIDDEN","role","ADMIN")))error(call("PATCH",endpoint,admin,body),400,40001);
            error(h.request("PATCH",endpoint,admin.token(),"{\"status\":null}",Map.of()),400,40001);
        }
        for(String query:List.of("page=0","size=51","page=9223372036854775807&size=50","status=HIDDEN","role=ROOT"))error(call("GET","/api/v1/admin/users?"+query,admin,null),400,40001);
        error(call("GET","/api/v1/admin/guides?status=ACTIVE",admin,null),400,40001);error(call("GET","/api/v1/admin/comments?status=PUBLISHED",admin,null),400,40001);
        assertThat(userRow(admin,user).path("role").asText()).isEqualTo("USER");assertThat(userRow(admin,user).path("status").asText()).isEqualTo("ACTIVE");
    }
    @Test void usersCannotDisableThemselvesAndRepeatedStatusDoesNotRewriteTime(){
        Account admin=h.account(true),user=h.account(false);JsonNode g=guide(user,"visible","PUBLISHED");comment(user,g,"visible");
        JsonNode before=userRow(admin,admin);error(call("PATCH",path("users",admin.id()),admin,Map.of("status","DISABLED")),409,40902);assertThat(userRow(admin,admin)).isEqualTo(before);
        assertThat(patch("users",user.id(),"DISABLED",admin).path("status").asText()).isEqualTo("DISABLED");error(call("GET","/api/v1/users/me",user,null),401,40101);error(call("GET","/api/v1/itineraries",user,null),401,40101);assertThat(get(guidePath(g),null).path("id")).isEqualTo(g.path("id"));
        assertThat(get(guidePath(g)+"/comments",null).path("total").asInt()).isEqualTo(1);assertThat(get(guidePath(g),null).path("commentCount").asInt()).isEqualTo(1);
        h.jdbc().update("UPDATE users SET updated_at='2026-10-01 00:00:00.000' WHERE id=? AND username=?",user.id(),user.username());
        assertThat(patch("users",user.id(),"DISABLED",admin).path("updatedAt").asText()).isEqualTo("2026-10-01T00:00:00.000+08:00");
        patch("users",user.id(),"ACTIVE",admin);assertThat(get("/api/v1/users/me",user).path("id").asText()).isEqualTo(user.id());
        error(call("PATCH",path("users","9223372036854775807"),admin,Map.of("status","DISABLED")),404,40401);
    }
    @Test void disabledAndDeletedAdministratorsDoNotBecomeAvailableOrRevive(){
        Account active=h.account(true),disabled=h.account(true),deleted=h.account(true);
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",disabled.id(),disabled.username());
        h.jdbc().update("UPDATE users SET is_deleted=1 WHERE id=? AND username=?",deleted.id(),deleted.username());
        assertThat(page("users",active,"&role=ADMIN").path("total").asInt()).isEqualTo(2);
        assertThat(page("users",active,"&role=ADMIN&status=ACTIVE").path("total").asInt()).isEqualTo(1);
        assertThat(page("users",active,"&role=ADMIN&status=DISABLED").path("total").asInt()).isEqualTo(1);
        error(call("PATCH",path("users",deleted.id()),active,Map.of("status","ACTIVE")),404,40401);
        error(call("GET","/api/v1/admin/users",disabled,null),401,40101);error(call("GET","/api/v1/admin/users",deleted,null),401,40101);
        error(call("PATCH",path("users",active.id()),active,Map.of("status","DISABLED")),409,40902);
    }
    @Test void concurrentCrossDisablingRetainsAUsableAdministrator() throws Exception {
        Account a=h.account(true),b=h.account(true);ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{
            Future<Result> one=executor.submit(()->{start.await();return call("PATCH",path("users",b.id()),a,Map.of("status","DISABLED"));});
            Future<Result> two=executor.submit(()->{start.await();return call("PATCH",path("users",a.id()),b,Map.of("status","DISABLED"));});
            start.countDown();List<Integer>statuses=List.of(one.get(25,TimeUnit.SECONDS).status(),two.get(25,TimeUnit.SECONDS).status());assertThat(statuses).containsExactlyInAnyOrder(200,401);
            assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE id IN (?,?) AND role='ADMIN' AND status='ACTIVE' AND is_deleted=0",Integer.class,a.id(),b.id())).isEqualTo(1);
        }finally{executor.shutdownNow();}
    }
    @Test void administratorWaitingOnTheSetLockMustRecheckItsOwnCurrentStatus() throws Exception {
        Account a=h.account(true);h.account(true);Account target=h.account(false);ExecutorService executor=Executors.newSingleThreadExecutor();
        try(var connection=source.getConnection()){
            connection.setAutoCommit(false);try(var statement=connection.prepareStatement("UPDATE users SET status='DISABLED' WHERE id=? AND username=?")){statement.setLong(1,Long.parseLong(a.id()));statement.setString(2,a.username());assertThat(statement.executeUpdate()).isEqualTo(1);}
            try{
                Future<Result> waiting=executor.submit(()->call("PATCH",path("users",target.id()),a,Map.of("status","DISABLED")));assertThatThrownBy(()->waiting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                connection.commit();error(waiting.get(25,TimeUnit.SECONDS),401,40101);assertThat(h.jdbc().queryForObject("SELECT status FROM users WHERE id=?",String.class,target.id())).isEqualTo("ACTIVE");
            }finally{connection.rollback();}
        }finally{executor.shutdownNow();}
    }
    @Test void guideHidingAndRestorationPreservePublicationAndAllRelationships(){
        Account admin=h.account(true),author=h.account(false),reader=h.account(false);JsonNode g=guide(author,"published","PUBLISHED"),c=comment(reader,g,"comment");String id=g.path("id").asText();
        success(call("PUT",guidePath(g)+"/like",reader,null),200);success(call("PUT",guidePath(g)+"/favorite",reader,null),200);
        h.jdbc().update("INSERT INTO guide_images(guide_id,image_url,sort_order) VALUES (?, '/uploads/2026/11/00000000000000000000000000000001.png',0)",id);
        JsonNode original=get(guidePath(g),null),hidden=patch("guides",id,"HIDDEN",admin);assertThat(hidden.path("status").asText()).isEqualTo("HIDDEN");assertThat(hidden.path("publishedAt")).isEqualTo(original.path("publishedAt"));assertThat(hidden.size()).isEqualTo(18);
        error(call("GET",guidePath(g),null,null),404,40401);error(call("GET",guidePath(g)+"/comments",null,null),404,40401);error(call("PUT",guidePath(g)+"/like",reader,null),404,40401);
        assertThat(get("/api/v1/users/me/favorites?keyword="+encoded(h.prefix()),reader).path("total").asInt()).isZero();
        for(String action:List.of("publish","unpublish"))error(call("POST",guidePath(g)+"/"+action,author,null),409,40902);
        h.jdbc().update("UPDATE guides SET updated_at='2026-10-01 00:00:00.000' WHERE id=? AND author_id=?",id,author.id());assertThat(patch("guides",id,"HIDDEN",admin).path("updatedAt").asText()).isEqualTo("2026-10-01T00:00:00.000+08:00");
        patch("guides",id,"PUBLISHED",admin);JsonNode restored=get(guidePath(g),null);
        for(String field:List.of("id","title","content","author","publishedAt","images","tags","likeCount","favoriteCount","commentCount"))assertThat(restored.path(field)).as(field).isEqualTo(original.path(field));
        assertThat(get("/api/v1/users/me/favorites?keyword="+encoded(h.prefix()),reader).path("total").asInt()).isEqualTo(1);
        assertThat(c.path("guideId").asText()).isEqualTo(id);
    }
    @Test void moderationNeverPublishesDraftsOrAbnormalHiddenRowsAndCannotRestoreDeletedGuides(){
        Account admin=h.account(true),author=h.account(false);JsonNode g=guide(author,"draft","DRAFT");String id=g.path("id").asText();
        for(String status:List.of("PUBLISHED","HIDDEN"))error(call("PATCH",path("guides",id),admin,Map.of("status",status)),409,40902);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=? AND author_id=? AND published_at IS NULL",id,author.id());error(call("PATCH",path("guides",id),admin,Map.of("status","PUBLISHED")),409,40902);
        success(call("DELETE",guidePath(g),author,null),200);error(call("PATCH",path("guides",id),admin,Map.of("status","PUBLISHED")),404,40401);
    }
    @Test void contentListsUseRealPaginationStatusLiteralSearchAndSafeAuthorProjections(){
        Account admin=h.account(true),author=h.account(false);JsonNode g1=guide(author,"literal%_!\\","PUBLISHED"),g2=guide(author,"second","PUBLISHED"),draft=guide(author,"draft","DRAFT");JsonNode c1=comment(author,g1,"literal%_!\\"),c2=comment(author,g2,"second");
        patch("guides",g2.path("id").asText(),"HIDDEN",admin);patch("comments",c2.path("id").asText(),"HIDDEN",admin);
        h.jdbc().update("UPDATE guides SET created_at='2026-11-01 00:00:00.000' WHERE author_id=?",author.id());h.jdbc().update("UPDATE guide_comments SET created_at='2026-11-01 00:00:00.000' WHERE user_id=?",author.id());
        assertThat(page("guides",admin,"&size=1").path("records").get(0).path("id")).isEqualTo(draft.path("id"));assertThat(page("guides",admin,"&status=HIDDEN").path("total").asInt()).isEqualTo(1);assertThat(page("guides",admin,"&status=DRAFT").path("total").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/admin/guides?keyword="+encoded(g1.path("title").asText()),admin).path("total").asInt()).isEqualTo(1);
        JsonNode comments=page("comments",admin,"&size=1");assertThat(comments.path("total").asInt()).isEqualTo(2);assertThat(comments.path("records").get(0).path("id")).isEqualTo(c2.path("id"));
        JsonNode exact=get("/api/v1/admin/comments?keyword="+encoded(c1.path("content").asText()),admin).path("records").get(0);assertThat(exact.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","guideId","guideTitle","author","content","status","createdAt","updatedAt");assertThat(exact.path("author").size()).isEqualTo(5);assertThat(exact.path("guideTitle")).isEqualTo(g1.path("title"));assertThat(exact.path("id").isTextual()).isTrue();
        JsonNode beyond=page("comments",admin,"&page=999&size=1");assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("total").asInt()).isEqualTo(2);assertThat(beyond.path("current").asInt()).isEqualTo(999);
    }
    @Test void commentHidingChangesOnlyPublicCountAndDeletedCommentsNeverRevive(){
        Account admin=h.account(true),author=h.account(false);JsonNode g=guide(author,"comments","PUBLISHED"),c=comment(author,g,"comments");String id=c.path("id").asText();
        JsonNode hidden=patch("comments",id,"HIDDEN",admin);assertThat(hidden.path("content")).isEqualTo(c.path("content"));assertThat(get(guidePath(g),null).path("commentCount").asInt()).isZero();assertThat(get(guidePath(g)+"/comments",null).path("total").asInt()).isZero();
        h.jdbc().update("UPDATE guide_comments SET updated_at='2026-10-01 00:00:00.000' WHERE id=? AND user_id=?",id,author.id());assertThat(patch("comments",id,"HIDDEN",admin).path("updatedAt").asText()).isEqualTo("2026-10-01T00:00:00.000+08:00");
        patch("comments",id,"NORMAL",admin);assertThat(get(guidePath(g),null).path("commentCount").asInt()).isEqualTo(1);
        error(call("DELETE","/api/v1/comments/"+id,admin,null),403,40301);
        success(call("DELETE","/api/v1/comments/"+id,author,null),200);error(call("PATCH",path("comments",id),admin,Map.of("status","NORMAL")),404,40401);assertThat(page("comments",admin,"").path("total").asInt()).isZero();
    }
    @Test void nonpublicParentsStillAllowAdministrativeCommentGovernanceWithoutExposure(){
        Account admin=h.account(true),author=h.account(false);JsonNode g=guide(author,"deleted-parent","PUBLISHED"),c=comment(author,g,"deleted-parent");
        success(call("DELETE",guidePath(g),author,null),200);String id=c.path("id").asText();assertThat(page("comments",admin,"").path("total").asInt()).isEqualTo(1);
        patch("comments",id,"HIDDEN",admin);patch("comments",id,"NORMAL",admin);error(call("GET",guidePath(g)+"/comments",null,null),404,40401);success(call("DELETE","/api/v1/comments/"+id,author,null),200);
    }
    @Test void waitingCommentRestorationSeesCommittedLogicalDeletion() throws Exception {
        Account admin=h.account(true),author=h.account(false);JsonNode g=guide(author,"wait","PUBLISHED"),c=comment(author,g,"wait");String id=c.path("id").asText();patch("comments",id,"HIDDEN",admin);ExecutorService executor=Executors.newSingleThreadExecutor();
        try(var connection=source.getConnection()){
            connection.setAutoCommit(false);try(var lock=connection.prepareStatement("SELECT id FROM guides WHERE id=? FOR UPDATE")){lock.setLong(1,Long.parseLong(g.path("id").asText()));try(var found=lock.executeQuery()){assertThat(found.next()).isTrue();}}
            try(var change=connection.prepareStatement("UPDATE guide_comments SET is_deleted=1 WHERE id=? AND user_id=?")){change.setLong(1,Long.parseLong(id));change.setLong(2,Long.parseLong(author.id()));assertThat(change.executeUpdate()).isEqualTo(1);}
            try{Future<Result> waiting=executor.submit(()->call("PATCH",path("comments",id),admin,Map.of("status","NORMAL")));assertThatThrownBy(()->waiting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);connection.commit();error(waiting.get(25,TimeUnit.SECONDS),404,40401);assertThat(h.jdbc().queryForObject("SELECT status FROM guide_comments WHERE id=?",String.class,id)).isEqualTo("HIDDEN");}finally{connection.rollback();}
        }finally{executor.shutdownNow();}
    }
}
