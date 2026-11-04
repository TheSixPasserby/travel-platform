package com.example.travelplatform.administration;
import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
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
class AdminDashboardApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    private static final String URL="/api/v1/admin/dashboard/summary";
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m6a");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupGuides();}finally{try{h.cleanupDestinations();}finally{h.cleanupAccounts();}}}}
    private JsonNode summary(Account a){return success(h.json("GET",URL,a.token(),null),200);}
    private JsonNode guide(Account a,String suffix,String status){return success(h.json("POST","/api/v1/guides",a.token(),Map.of("title",h.named(suffix),"content","后台统计验收","status",status)),201);}
    private JsonNode comment(Account a,JsonNode guide,String suffix){return success(h.json("POST","/api/v1/guides/"+guide.path("id").asText()+"/comments",a.token(),Map.of("content",h.named(suffix))),201);}
    @Test void dashboardIsAdministratorOnlyAndProjectsExactlyFourNumericCounts(){
        error(h.json("GET",URL,null,null),401,40101);Account user=h.account(false),admin=h.account(true);
        error(h.json("GET",URL,user.token(),null),403,40301);JsonNode data=summary(admin);
        assertThat(data.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("userCount","guideCount","destinationCount","commentCount");
        for(JsonNode value:data)assertThat(value.isIntegralNumber()).isTrue();
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",admin.id(),admin.username());error(h.json("GET",URL,admin.token(),null),401,40101);
    }
    @Test void countsIncludeInactiveAndHiddenRowsButExcludeLogicalDeletion(){
        Account admin=h.account(true);JsonNode before=summary(admin);Account author=h.account(false),deletedUser=h.account(false);
        JsonNode published=guide(author,"published","PUBLISHED");guide(author,"draft","DRAFT");JsonNode hidden=guide(author,"hidden","PUBLISHED"),deleted=guide(author,"deleted","PUBLISHED");
        comment(author,published,"normal");JsonNode hiddenComment=comment(author,published,"hidden"),deletedComment=comment(author,published,"deleted");comment(author,deleted,"retained-parent");
        success(h.json("PATCH","/api/v1/admin/guides/"+hidden.path("id").asText()+"/status",admin.token(),Map.of("status","HIDDEN")),200);
        success(h.json("PATCH","/api/v1/admin/comments/"+hiddenComment.path("id").asText()+"/status",admin.token(),Map.of("status","HIDDEN")),200);
        success(h.json("DELETE","/api/v1/comments/"+deletedComment.path("id").asText(),author.token(),null),200);
        success(h.json("DELETE","/api/v1/guides/"+deleted.path("id").asText(),author.token(),null),200);
        for(int n=0;n<3;n++){
            JsonNode destination=success(h.json("POST","/api/v1/admin/destinations",admin.token(),Map.of("name",h.named("destination"+n))),201);
            if(n==1)success(h.json("PATCH","/api/v1/admin/destinations/"+destination.path("id").asText()+"/status",admin.token(),Map.of("status","INACTIVE")),200);
            if(n==2)h.jdbc().update("UPDATE destinations SET is_deleted=1 WHERE id=? AND name=?",destination.path("id").asText(),h.named("destination"+n));
        }
        h.jdbc().update("UPDATE users SET is_deleted=1 WHERE id=? AND username=?",deletedUser.id(),deletedUser.username());
        success(h.json("PATCH","/api/v1/admin/users/"+author.id()+"/status",admin.token(),Map.of("status","DISABLED")),200);
        JsonNode after=summary(admin);assertThat(after.path("userCount").asLong()-before.path("userCount").asLong()).isEqualTo(1);
        assertThat(after.path("guideCount").asLong()-before.path("guideCount").asLong()).isEqualTo(3);
        assertThat(after.path("destinationCount").asLong()-before.path("destinationCount").asLong()).isEqualTo(2);
        assertThat(after.path("commentCount").asLong()-before.path("commentCount").asLong()).isEqualTo(3);
    }
}
