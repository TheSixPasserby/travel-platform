package com.example.travelplatform.comment;

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
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommentApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m4i");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupGuides();}finally{h.cleanupAccounts();}}}
    private JsonNode guide(Account a,String suffix){return success(h.json("POST","/api/v1/guides",a.token(),Map.of("title",h.named(suffix),"content","评论验收攻略","status","PUBLISHED")),201);}
    private String base(JsonNode g){return "/api/v1/guides/"+g.path("id").asText();}
    private String comments(JsonNode g){return base(g)+"/comments";}
    private JsonNode create(Account a,JsonNode g,String content){return success(h.json("POST",comments(g),a.token(),Map.of("content",content)),201);}
    private Result get(String path){return h.json("GET",path,null,null);}
    private Result remove(Account a,JsonNode c){return h.json("DELETE","/api/v1/comments/"+c.path("id").asText(),a==null?null:a.token(),null);}

    @Test void publicReadsAndCommentSafeProjectionDoNotExposeAccountInternals() {
        Account a=h.account(false),b=h.account(false);JsonNode g=guide(b,"public");
        assertThat(success(get(comments(g)),200).path("total").asLong()).isZero();
        JsonNode c=create(a,g," \u00A0\u200B溪谷\r\n很好\uFEFF ");
        assertThat(c.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","guideId","author","content","createdAt","updatedAt");
        assertThat(c.path("id").isTextual()).isTrue();assertThat(c.path("guideId")).isEqualTo(g.path("id"));assertThat(c.path("content").asText()).isEqualTo("溪谷\n很好");
        assertThat(c.path("author").propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","nickname","avatarUrl","bio");
        assertThat(c.path("author").path("id").asText()).isEqualTo(a.id());assertThat(c.path("createdAt").asText()).matches(".*\\.\\d{3}\\+08:00");
        error(h.json("POST",comments(g),null,Map.of("content","匿名评论")),401,40101);error(remove(null,c),401,40101);
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",a.id(),a.username());
        assertThat(success(get(comments(g)),200).path("total").asLong()).isEqualTo(1);
        error(h.json("POST",comments(g),a.token(),Map.of("content","过期会话")),401,40101);
    }

    @Test void malformedTypesUnknownFieldsControlsAndCodePointOverflowNeverCreateRows() {
        Account a=h.account(false);JsonNode g=guide(a,"validation");
        assertThat(create(a,g,"🌿".repeat(2000)).path("content").asText()).hasSize(4000);
        List<Object> bad=new ArrayList<>();bad.add(Map.of());bad.add(Collections.singletonMap("content",null));
        bad.add(Map.of("content",1));bad.add(Map.of("content",true));bad.add(Map.of("content","🌿".repeat(2001)));
        bad.add(Map.of("content","\u00A0\u200B\uFEFF"));bad.add(Map.of("content","a\u0000b"));bad.add(Map.of("content","a\u0085b"));
        bad.add(Map.of("content","正常","authorId",a.id()));bad.add(Map.of("content","正常","status","NORMAL"));bad.add(Map.of("content","正常","guideId",g.path("id").asText()));
        for(Object body:bad)error(h.json("POST",comments(g),a.token(),body),400,40001);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE guide_id=?",Integer.class,g.path("id").asText())).isEqualTo(1);
    }

    @Test void onlyCommentAuthorCanDeleteEvenWhenOtherUserOwnsGuideOrIsAdmin() {
        Account a=h.account(false),owner=h.account(false),admin=h.account(true);JsonNode g=guide(owner,"ownership"),c=create(a,g,"只有作者能删除");
        error(remove(owner,c),403,40301);error(remove(admin,c),403,40301);
        assertThat(success(remove(a,c),200).isNull()).isTrue();error(remove(a,c),404,40401);
        assertThat(h.jdbc().queryForObject("SELECT is_deleted FROM guide_comments WHERE id=? AND user_id=?",Integer.class,c.path("id").asText(),a.id())).isEqualTo(1);
        assertThat(success(get(comments(g)),200).path("total").asLong()).isZero();assertThat(success(get(base(g)),200).path("commentCount").asLong()).isZero();
    }

    @Test void authorCanDeleteHiddenCommentAndCommentsUnderNonpublicOrDeletedParents() {
        Account a=h.account(false),owner=h.account(false);JsonNode g=guide(owner,"hiddencomment"),c=create(a,g,"隐藏评论");
        h.jdbc().update("UPDATE guide_comments SET status='HIDDEN' WHERE id=? AND user_id=? AND guide_id=?",c.path("id").asText(),a.id(),g.path("id").asText());
        assertThat(success(get(comments(g)),200).path("total").asLong()).isZero();error(remove(owner,c),403,40301);success(remove(a,c),200);
        for(String state:List.of("DRAFT","HIDDEN","DELETED")) {
            JsonNode parent=guide(owner,"parent"+state),comment=create(a,parent,"仍属于评论作者");
            if(state.equals("DRAFT"))success(h.json("POST",base(parent)+"/unpublish",owner.token(),null),200);
            else if(state.equals("DELETED"))success(h.json("DELETE",base(parent),owner.token(),null),200);
            else h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=? AND author_id=?",parent.path("id").asText(),owner.id());
            error(get(comments(parent)),404,40401);error(remove(owner,comment),403,40301);success(remove(a,comment),200);
        }
    }

    @Test void publicPaginationCountsOnlyNormalUndeletedCommentsAndUsesStableNewestOrder() {
        Account a=h.account(false),b=h.account(false);JsonNode g=guide(a,"pagination");
        JsonNode first=create(a,g,"第一条"),second=create(b,g,"第二条"),third=create(a,g,"第三条"),hidden=create(b,g,"隐藏"),deleted=create(b,g,"删除");
        h.jdbc().update("UPDATE guide_comments SET status='HIDDEN' WHERE id=? AND user_id=?",hidden.path("id").asText(),b.id());
        success(remove(b,deleted),200);
        h.jdbc().update("UPDATE guide_comments SET created_at='2026-11-01 00:00:00.000' WHERE guide_id=?",g.path("id").asText());
        JsonNode page=success(get(comments(g)+"?size=1"),200);assertThat(page.path("total").asLong()).isEqualTo(3);assertThat(page.path("pages").asLong()).isEqualTo(3);assertThat(page.path("records").get(0).path("id")).isEqualTo(third.path("id"));
        assertThat(success(get(comments(g)+"?size=1&page=2"),200).path("records").get(0).path("id")).isEqualTo(second.path("id"));
        assertThat(success(get(comments(g)+"?size=1&page=3"),200).path("records").get(0).path("id")).isEqualTo(first.path("id"));
        JsonNode beyond=success(get(comments(g)+"?size=1&page=999"),200);assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("total").asLong()).isEqualTo(3);assertThat(beyond.path("current").asLong()).isEqualTo(999);
        assertThat(success(get(base(g)),200).path("commentCount").asLong()).isEqualTo(3);
        for(String query:List.of("page=0","size=51","page=9223372036854775807&size=50"))error(get(comments(g)+"?"+query),400,40001);
    }

    @Test void nonpublicGuidesRejectNewCommentsAndRetainPriorCommentsForRestoration() {
        Account a=h.account(false);JsonNode g=guide(a,"visibility");create(a,g,"原有评论");
        success(h.json("POST",base(g)+"/unpublish",a.token(),null),200);
        error(get(comments(g)),404,40401);error(h.json("POST",comments(g),a.token(),Map.of("content","新评论")),404,40401);
        success(h.json("POST",base(g)+"/publish",a.token(),null),200);assertThat(success(get(comments(g)),200).path("total").asLong()).isEqualTo(1);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=? AND author_id=?",g.path("id").asText(),a.id());
        error(get(comments(g)),404,40401);error(h.json("POST",comments(g),a.token(),Map.of("content","新评论")),404,40401);
        h.jdbc().update("UPDATE guides SET status='PUBLISHED' WHERE id=? AND author_id=?",g.path("id").asText(),a.id());
        assertThat(success(get(comments(g)),200).path("total").asLong()).isEqualTo(1);
        success(h.json("DELETE",base(g),a.token(),null),200);error(h.json("POST",comments(g),a.token(),Map.of("content","新评论")),404,40401);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE guide_id=?",Integer.class,g.path("id").asText())).isEqualTo(1);
    }

    @Test void htmlAndSqlPayloadRemainPlainTextAndDoNotChangeOtherRecords() {
        Account a=h.account(false);JsonNode g=guide(a,"plain");String content="<img src=x onerror=alert(1)> '); DROP TABLE users; --";
        JsonNode c=create(a,g,content);assertThat(c.path("content").asText()).isEqualTo(content);
        JsonNode listed=success(get(comments(g)),200).path("records").get(0);assertThat(listed.path("content").asText()).isEqualTo(content);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND username=?",Integer.class,a.id(),a.username())).isEqualTo(1);
    }

    @Test void commentWaitingOnGuideLockRejectsACommittedWithdrawal() throws Exception {
        Account a=h.account(false);JsonNode g=guide(a,"lock");ExecutorService executor=Executors.newSingleThreadExecutor();
        try(var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("UPDATE guides SET status='DRAFT' WHERE id=? AND author_id=?")){statement.setLong(1,Long.parseLong(g.path("id").asText()));statement.setLong(2,Long.parseLong(a.id()));assertThat(statement.executeUpdate()).isEqualTo(1);}
            try {
                Future<Result> result=executor.submit(()->h.json("POST",comments(g),a.token(),Map.of("content","不能迟到写入")));
                assertThatThrownBy(()->result.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                connection.commit();error(result.get(25,TimeUnit.SECONDS),404,40401);
                assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE guide_id=?",Integer.class,g.path("id").asText())).isZero();
            } finally {connection.rollback();}
        } finally {executor.shutdownNow();}
    }
}
