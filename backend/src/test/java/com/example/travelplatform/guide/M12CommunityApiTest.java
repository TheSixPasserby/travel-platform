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
class M12CommunityApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m3g");}
    @AfterEach void cleanup(){try{h.cleanupGuides();}finally{h.cleanupAccounts();}}
    String guide(HttpTestSupport.Account a,String status,String suffix){
        return success(h.json("POST","/api/v1/guides",a.token(),Map.of("title",h.named(suffix),"content","旅行记录","status",status)),201).path("id").asText();
    }
    String creator(HttpTestSupport.Account a){return "/api/v1/users/by-username/"+a.username();}
    @Test void publicCreatorExposesOnlySafeProfileAndPublishedCountWithDatabasePagination(){
        var a=h.account(false);var b=h.account(false);
        var published=guide(a,"PUBLISHED","public");guide(a,"DRAFT","draft");
        var hidden=guide(a,"PUBLISHED","hidden");h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=?",hidden);
        var deleted=guide(a,"PUBLISHED","deleted");success(h.json("DELETE","/api/v1/guides/"+deleted,a.token(),null),200);
        guide(b,"PUBLISHED","other");
        var profile=success(h.json("GET",creator(a),null,null),200);
        assertThat(profile.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","username","nickname","avatarUrl","bio","publishedGuideCount","likeCount","favoriteCount");
        assertThat(profile.path("publishedGuideCount").asLong()).isEqualTo(1);
        var page=success(h.json("GET",creator(a)+"/guides?page=1&size=1",null,null),200);
        assertThat(page.path("total").asLong()).isEqualTo(1);
        assertThat(page.path("records")).hasSize(1);
        assertThat(page.path("records").get(0).path("id").asText()).isEqualTo(published);
        assertThat(success(h.json("GET",creator(a)+"/guides?page=2&size=1",null,null),200).path("records")).isEmpty();
        assertThat(h.json("GET",creator(a)+"/guides?page=0",null,null).status()).isEqualTo(400);
        assertThat(h.json("GET","/api/v1/users/by-username/absent_creator_12",null,null).status()).isEqualTo(404);
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=?",a.id());
        assertThat(h.json("GET",creator(a),null,null).status()).isEqualTo(404);
        assertThat(h.json("GET",creator(a)+"/guides",null,null).status()).isEqualTo(404);
    }
    @Test void relatedUsesSharedDestinationOrTagAndNeverReturnsCurrentDraftHiddenOrDeleted(){
        var a=h.account(false);var first=guide(a,"PUBLISHED","first");var match=guide(a,"PUBLISHED","match");
        var draft=guide(a,"DRAFT","draft");var hidden=guide(a,"PUBLISHED","hidden");var deleted=guide(a,"PUBLISHED","deleted");
        var unrelated=guide(a,"PUBLISHED","unrelated");
        Long tag=h.jdbc().queryForObject("SELECT id FROM tags ORDER BY id LIMIT 1",Long.class);
        for(String id:List.of(first,match,draft,hidden,deleted))h.jdbc().update("INSERT INTO guide_tags(guide_id,tag_id) VALUES (?,?)",id,tag);
        h.jdbc().update("UPDATE guides SET status='HIDDEN' WHERE id=?",hidden);
        success(h.json("DELETE","/api/v1/guides/"+deleted,a.token(),null),200);
        var data=success(h.json("GET","/api/v1/guides/"+first+"/related?size=12",null,null),200);
        var ids=new ArrayList<String>();data.forEach(node->ids.add(node.path("id").asText()));
        assertThat(ids).contains(match).doesNotContain(first,draft,hidden,deleted,unrelated);
        assertThat(h.json("GET","/api/v1/guides/"+draft+"/related",null,null).status()).isEqualTo(404);
        assertThat(h.json("GET","/api/v1/guides/"+hidden+"/related",a.token(),null).status()).isEqualTo(404);
        assertThat(h.json("GET","/api/v1/guides/"+first+"/related?size=13",null,null).status()).isEqualTo(400);
        Long city=h.jdbc().queryForObject("SELECT id FROM destinations WHERE destination_type='CITY' AND is_deleted=0 ORDER BY id LIMIT 1",Long.class);
        h.jdbc().update("UPDATE guides SET destination_id=? WHERE id IN (?,?)",city,first,unrelated);
        ids.clear();success(h.json("GET","/api/v1/guides/"+first+"/related",null,null),200).forEach(node->ids.add(node.path("id").asText()));
        assertThat(ids).contains(match,unrelated);
    }
    @Test void historicalIpFieldsStayNullableButNeverDiscloseUnverifiedText(){
        var a=h.account(false);var id=guide(a,"PUBLISHED","ip");
        h.jdbc().update("UPDATE guides SET ip_region='历史未验证属地' WHERE id=?",id);
        assertThat(success(h.json("GET","/api/v1/guides/"+id,null,null),200).path("ipRegion").isNull()).isTrue();
        var comment=success(h.json("POST","/api/v1/guides/"+id+"/comments",a.token(),Map.of("content","评论")),201);
        h.jdbc().update("UPDATE guide_comments SET ip_region='192.0.2.3' WHERE id=?",comment.path("id").asText());
        var comments=success(h.json("GET","/api/v1/guides/"+id+"/comments",null,null),200);
        assertThat(comments.path("records").get(0).path("ipRegion").isNull()).isTrue();
    }
}
