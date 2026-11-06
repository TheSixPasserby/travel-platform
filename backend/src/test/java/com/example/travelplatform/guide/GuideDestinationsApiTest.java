package com.example.travelplatform.guide;
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
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GuideDestinationsApiTest {
    @Autowired DataSource source;@LocalServerPort int port;HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m3g");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupGuides();h.cleanupDestinations();}finally{h.cleanupAccounts();}}}
    JsonNode destination(Account admin,String name,String type,String parent){Map<String,Object>b=new LinkedHashMap<>();b.put("name",h.named(name));b.put("destinationType",type);if(parent!=null)b.put("parentId",parent);return success(h.json("POST","/api/v1/admin/destinations",admin.token(),b),201);}
    Map<String,Object> guide(JsonNode city,List<String> spots,String status){Map<String,Object>b=new LinkedHashMap<>();b.put("title",h.named("guide%_!"));b.put("content","# 原创\n景点安排。");b.put("destinationId",city.path("id").asText());b.put("scenicSpotIds",spots);b.put("status",status);return b;}
    JsonNode create(Account author,Map<String,Object>b){return success(h.json("POST","/api/v1/guides",author.token(),b),201);}
    Map<String,Object> edit(JsonNode guide,JsonNode city){return new LinkedHashMap<>(Map.of("title",guide.path("title").asText(),"content",guide.path("content").asText(),"destinationId",city.path("id").asText()));}
    String id(JsonNode n){return n.path("id").asText();}

    @Test void createsRealSameCityLinksAndOldClientOmissionPreservesThem(){
        Account admin=h.account(true),author=h.account(false);JsonNode city=destination(admin,"city","CITY",null),spot=destination(admin,"spot","SCENIC_SPOT",id(city));
        JsonNode g=create(author,guide(city,List.of(id(spot)),"PUBLISHED"));assertThat(g.path("scenicSpots").size()).isEqualTo(1);
        JsonNode kept=success(h.json("PUT","/api/v1/guides/"+id(g),author.token(),edit(g,city)),200);assertThat(kept.path("scenicSpots").get(0).path("id").asText()).isEqualTo(id(spot));
        Map<String,Object> clear=edit(kept,city);clear.put("scenicSpotIds",List.of());
        assertThat(success(h.json("PUT","/api/v1/guides/"+id(g),author.token(),clear),200).path("scenicSpots").size()).isZero();
    }

    @Test void rejectsCrossCityDuplicateInactiveAndCityAsSpot(){
        Account admin=h.account(true),author=h.account(false);JsonNode a=destination(admin,"cityA","CITY",null),b=destination(admin,"cityB","CITY",null),spot=destination(admin,"spot","SCENIC_SPOT",id(a));
        for(List<String> ids:List.of(List.of(id(spot),"0"+id(spot)),List.of(id(a)),List.of("9223372036854775807")))
            error(h.json("POST","/api/v1/guides",author.token(),guide(a,ids,"DRAFT")),400,40001);
        error(h.json("POST","/api/v1/guides",author.token(),guide(b,List.of(id(spot)),"DRAFT")),400,40001);
        success(h.json("PATCH","/api/v1/admin/destinations/"+id(spot)+"/status",admin.token(),Map.of("status","INACTIVE")),200);
        error(h.json("POST","/api/v1/guides",author.token(),guide(a,List.of(id(spot)),"DRAFT")),400,40001);
    }

    @Test void relatedGuidesArePublishedPaginatedAndDeduplicated(){
        Account admin=h.account(true),author=h.account(false);JsonNode city=destination(admin,"city","CITY",null),a=destination(admin,"a","SCENIC_SPOT",id(city)),b=destination(admin,"b","SCENIC_SPOT",id(city));
        JsonNode published=create(author,guide(city,List.of(id(a),id(b)),"PUBLISHED"));create(author,guide(city,List.of(id(a)),"DRAFT"));
        String root="/api/v1/destinations/"+id(city)+"/guides";
        JsonNode page=success(h.request("GET",root+"?page=1&size=1",null,null,Map.of()),200);assertThat(page.path("total").asInt()).isEqualTo(1);assertThat(page.path("records").get(0).path("id").asText()).isEqualTo(id(published));
        assertThat(success(h.request("GET",root+"?page=2&size=1",null,null,Map.of()),200).path("records").size()).isZero();
        assertThat(success(h.request("GET","/api/v1/destinations/"+id(a)+"/guides",null,null,Map.of()),200).path("total").asInt()).isEqualTo(1);
    }

    @Test void ownershipAndMainCityChangeCannotBypassLinkValidation(){
        Account admin=h.account(true),author=h.account(false),other=h.account(false);JsonNode a=destination(admin,"cityA","CITY",null),b=destination(admin,"cityB","CITY",null),spot=destination(admin,"spot","SCENIC_SPOT",id(a));
        JsonNode g=create(author,guide(a,List.of(id(spot)),"DRAFT"));
        error(h.json("PUT","/api/v1/guides/"+id(g),other.token(),edit(g,a)),403,40301);
        error(h.json("POST","/api/v1/guides",null,guide(a,List.of(id(spot)),"DRAFT")),401,40101);
        error(h.json("PUT","/api/v1/guides/"+id(g),author.token(),edit(g,b)),400,40001);
        JsonNode own=success(h.request("GET","/api/v1/users/me/guides/"+id(g),author.token(),null,Map.of()),200);assertThat(own.path("destination").path("id").asText()).isEqualTo(id(a));
    }

    @Test void linkedSpotCannotBeMovedToAnotherCityByAdministrator(){
        Account admin=h.account(true),author=h.account(false);JsonNode a=destination(admin,"cityA","CITY",null),b=destination(admin,"cityB","CITY",null),spot=destination(admin,"spot","SCENIC_SPOT",id(a));
        create(author,guide(a,List.of(id(spot)),"DRAFT"));
        Map<String,Object> update=new LinkedHashMap<>(Map.of("name",spot.path("name").asText(),"destinationType","SCENIC_SPOT","parentId",id(b),"country","中国","status","ACTIVE","sortOrder",0));
        error(h.json("PUT","/api/v1/admin/destinations/"+id(spot),admin.token(),update),409,40902);
    }
}
