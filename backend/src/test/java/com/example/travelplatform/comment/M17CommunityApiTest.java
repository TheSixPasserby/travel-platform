package com.example.travelplatform.comment;

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
class M17CommunityApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup() { h = new HttpTestSupport(source, port, "m4i"); }
    @AfterEach void cleanup() { try { h.cleanupGuides(); } finally { h.cleanupAccounts(); } }
    String guide(Account a) {
        return success(h.json("POST", "/api/v1/guides", a.token(), Map.of("title",h.named("旅行"),"content","正文","status","PUBLISHED")),201).path("id").asText();
    }
    JsonNode comment(Account a,String g,Map<String,Object> payload) {
        return success(h.json("POST","/api/v1/guides/"+g+"/comments",a.token(),payload),201);
    }
    JsonNode get(Account a,String path) { return success(h.json("GET",path,a==null?null:a.token(),null),200); }

    @Test void repliesMentionsNotificationsAreAtomicScopedAndNavigable() {
        var owner=h.account(false);var writer=h.account(false);var reader=h.account(false);
        String g=guide(owner);
        var root=comment(writer,g,Map.of("content","请问交通？"));
        assertThat(get(owner,"/api/v1/notifications/unread-count").path("count").asLong()).isEqualTo(1);
        var reply=comment(owner,g,Map.of("content","@"+reader.username()+" 可以乘地铁","replyToId",root.path("id").asText(),"mentionUserIds",List.of(reader.id())));
        assertThat(reply.path("rootId")).isEqualTo(root.path("id"));
        assertThat(reply.path("mentions").get(0).path("id").asText()).isEqualTo(reader.id());
        var nested=comment(reader,g,Map.of("content","收到，谢谢","replyToId",reply.path("id").asText()));
        assertThat(nested.path("rootId")).isEqualTo(root.path("id"));
        assertThat(nested.path("replyToAuthor").path("id").asText()).isEqualTo(owner.id());
        var roots=get(null,"/api/v1/guides/"+g+"/comments");
        assertThat(roots.path("total").asLong()).isEqualTo(1);
        assertThat(roots.path("records").get(0).path("replyCount").asLong()).isEqualTo(2);
        assertThat(get(null,"/api/v1/comments/"+root.path("id").asText()+"/replies").path("total").asLong()).isEqualTo(2);
        var location=get(null,"/api/v1/comments/"+nested.path("id").asText()+"/location");
        assertThat(location.path("rootPage").asInt()).isEqualTo(1);
        assertThat(location.path("replyPage").asInt()).isEqualTo(1);
        var notification=get(reader,"/api/v1/notifications").path("records").get(0);
        assertThat(notification.path("type").asText()).isEqualTo("MENTION");
        assertThat(notification.path("href").asText()).contains("commentId="+reply.path("id").asText());
        String id=notification.path("id").asText();
        error(h.json("PUT","/api/v1/notifications/"+id+"/read",writer.token(),null),404,40401);
        success(h.json("PUT","/api/v1/notifications/"+id+"/read",reader.token(),null),200);
        success(h.json("PUT","/api/v1/notifications/"+id+"/read",reader.token(),null),200);
        assertThat(get(reader,"/api/v1/notifications/unread-count").path("count").asInt()).isZero();
        h.jdbc().update("UPDATE guide_comments SET status='HIDDEN' WHERE id=?",root.path("id").asText());
        assertThat(get(writer,"/api/v1/notifications").path("records")).isEmpty();
        error(h.json("GET","/api/v1/comments/"+nested.path("id").asText()+"/location",null,null),404,40401);
        error(h.json("GET","/api/v1/comments/"+root.path("id").asText()+"/replies",null,null),404,40401);
    }

    @Test void invalidMentionsCrossGuideRepliesAndAnonymousWritesLeaveNoSideEffects() {
        var a=h.account(false);var b=h.account(false);String g=guide(a),other=guide(a);
        var root=comment(b,other,Map.of("content","另一篇评论"));
        String path="/api/v1/guides/"+g+"/comments";
        error(h.json("POST",path,null,Map.of("content","匿名")),401,40101);
        error(h.json("POST",path,b.token(),Map.of("content","跨攻略","replyToId",root.path("id").asText())),400,40001);
        error(h.json("POST",path,b.token(),Map.of("content","正文无提及","mentionUserIds",List.of(a.id()))),400,40001);
        error(h.json("POST",path,b.token(),Map.of("content","@"+a.username()+"x","mentionUserIds",List.of(a.id()))),400,40001);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE guide_id=?",Long.class,g)).isZero();
        error(h.json("GET","/api/v1/users/mention-suggestions?keyword="+a.username(),null,null),401,40101);
        var suggestions=get(b,"/api/v1/users/mention-suggestions?keyword="+a.username());
        assertThat(suggestions.get(0).propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","nickname","username","avatarUrl","bio");
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=?",a.id());
        assertThat(get(b,"/api/v1/users/mention-suggestions?keyword="+a.username())).isEmpty();
    }

    @Test void frequencyGateAndNotificationRecipientDeduplicationUseCommittedDatabaseState() {
        var a=h.account(false);var b=h.account(false);String g=guide(a);
        comment(b,g,Map.of("content","@"+a.username()+" 你好","mentionUserIds",List.of(a.id())));
        assertThat(get(a,"/api/v1/notifications").path("total").asLong()).isEqualTo(1);
        for(int i=1;i<10;i++) comment(b,g,Map.of("content","旅行建议"+i));
        error(h.json("POST","/api/v1/guides/"+g+"/comments",b.token(),Map.of("content","过频")),429,42901);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE guide_id=?",Long.class,g)).isEqualTo(10);
        success(h.json("PUT","/api/v1/notifications/read-all",a.token(),null),200);
        assertThat(get(a,"/api/v1/notifications?unreadOnly=true").path("records")).isEmpty();
    }
    @Test void simultaneousCrossGuideMentionsDoNotDeadlockOrLoseNotifications() throws Exception {
        var a=h.account(false);var b=h.account(false);String ga=guide(a),gb=guide(b);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for(int round=0;round<8;round++) {
                var start=new java.util.concurrent.CountDownLatch(1);
                var one=executor.submit(()->{start.await();return h.json("POST","/api/v1/guides/"+gb+"/comments",a.token(),Map.of("content","@"+b.username()+" 同行建议","mentionUserIds",List.of(b.id())));});
                var two=executor.submit(()->{start.await();return h.json("POST","/api/v1/guides/"+ga+"/comments",b.token(),Map.of("content","@"+a.username()+" 同行建议","mentionUserIds",List.of(a.id())));});
                start.countDown();
                success(one.get(25,java.util.concurrent.TimeUnit.SECONDS),201);
                success(two.get(25,java.util.concurrent.TimeUnit.SECONDS),201);
            }
            assertThat(get(a,"/api/v1/notifications/unread-count").path("count").asLong()).isEqualTo(8);
            assertThat(get(b,"/api/v1/notifications/unread-count").path("count").asLong()).isEqualTo(8);
        } finally { executor.shutdownNow(); }
    }

    @Test void concurrentSameAccountCommentsAcrossGuidesRespectTheMinuteLimit() throws Exception {
        var a=h.account(false);var owner=h.account(false);String first=guide(owner),second=guide(owner);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var calls=new java.util.ArrayList<java.util.concurrent.Callable<HttpTestSupport.Result>>();
            for(int i=0;i<11;i++) {
                String target=i%2==0?first:second;
                calls.add(()->h.json("POST","/api/v1/guides/"+target+"/comments",a.token(),Map.of("content","并发旅行建议")));
            }
            var statuses=new java.util.ArrayList<Integer>();
            for(var future:executor.invokeAll(calls))statuses.add(future.get().status());
            assertThat(statuses.stream().filter(status->status==201).count()).isEqualTo(10);
            assertThat(statuses.stream().filter(status->status==429).count()).isEqualTo(1);
            assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM guide_comments WHERE user_id=?",Long.class,a.id())).isEqualTo(10);
        } finally { executor.shutdownNow(); }
    }

}
