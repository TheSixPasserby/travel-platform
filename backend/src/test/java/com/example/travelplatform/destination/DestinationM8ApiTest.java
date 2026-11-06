package com.example.travelplatform.destination;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DestinationM8ApiTest {
    @Autowired DataSource source;
    @LocalServerPort int port;
    HttpTestSupport h;
    private static final Path FILES;
    private static final String IMAGE="/uploads/2026/11/"+"e".repeat(32)+".png";
    static {
        try {
            FILES=Files.createTempDirectory("m8-destination-images-");
            Path image=FILES.resolve(IMAGE.substring("/uploads/".length()));
            Files.createDirectories(image.getParent());
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",image.toFile());
        }catch(Exception error){throw new ExceptionInInitializerError(error);}
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry r){r.add("travel.storage.upload-dir",()->FILES.toString());}
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m2d");}
    @AfterEach void cleanup(){if(h!=null){try{
        h.jdbc().update("DELETE FROM destination_images WHERE destination_id IN (SELECT id FROM destinations WHERE LEFT(name,?)=?)",h.prefix().length(),h.prefix());
        h.cleanupDestinations();
    }finally{h.cleanupAccounts();}}}
    @AfterAll static void files()throws Exception{try(var paths=Files.walk(FILES)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    Map<String,Object> input(String name,Map<String,Object> extra){Map<String,Object>b=new LinkedHashMap<>();b.put("name",h.named(name));b.putAll(extra);return b;}
    JsonNode create(Account a,String name,Map<String,Object> extra){return success(h.json("POST","/api/v1/admin/destinations",a.token(),input(name,extra)),201);}
    Map<String,Object> full(JsonNode d){Map<String,Object>b=new LinkedHashMap<>();for(String f:List.of("name","destinationType","country","status"))b.put(f,d.path(f).asText());b.put("sortOrder",d.path("sortOrder").asInt());b.put("parentId",d.path("parentId").isNull()?null:d.path("parentId").asText());return b;}
    Map<String,Object> attribution(){return Map.of("url",IMAGE,"altText","原创测试图","sourceUrl","https://example.org/source","author","Synthetic Author","license","CC0","licenseUrl","https://creativecommons.org/publicdomain/zero/1.0/","retrievedAt","2026-11-06T00:00:00.000+08:00");}

    @Test void cityHasNoParentAndSpotRequiresCity(){
        Account a=h.account(true);JsonNode city=create(a,"city",Map.of());
        error(h.json("POST","/api/v1/admin/destinations",a.token(),input("badCity",Map.of("parentId",city.path("id").asText()))),400,40001);
        error(h.json("POST","/api/v1/admin/destinations",a.token(),input("orphan",Map.of("destinationType","SCENIC_SPOT"))),400,40001);
        JsonNode spot=create(a,"spot",Map.of("destinationType","SCENIC_SPOT","parentId",city.path("id").asText()));
        error(h.json("POST","/api/v1/admin/destinations",a.token(),input("nested",Map.of("destinationType","SCENIC_SPOT","parentId",spot.path("id").asText()))),400,40001);
        Map<String,Object> change=full(city);change.put("destinationType","OTHER");
        error(h.json("PUT","/api/v1/admin/destinations/"+city.path("id").asText(),a.token(),change),409,40902);
    }

    @Test void detailsRoundTripAndOldClientPreservesNewFields(){
        Account a=h.account(true);
        JsonNode d=create(a,"details",Map.of("address","测试地址","longitude","104.000000","latitude","30.000000","recommendedDays",2,"sourceUrl","https://example.org/source","verifiedAt","2026-11-06T00:00:00.000+08:00"));
        assertThat(d.path("address").asText()).isEqualTo("测试地址");
        assertThat(d.path("longitude").asText()).isEqualTo("104.000000");
        JsonNode same=success(h.json("PUT","/api/v1/admin/destinations/"+d.path("id").asText(),a.token(),full(d)),200);
        assertThat(same.path("address").asText()).isEqualTo("测试地址");
        Map<String,Object> clear=full(same);clear.put("address",null);
        JsonNode cleared=success(h.json("PUT","/api/v1/admin/destinations/"+d.path("id").asText(),a.token(),clear),200);
        assertThat(cleared.path("address").isNull()).isTrue();
        assertThat(cleared.path("latitude").asText()).isEqualTo("30.000000");
    }

    @Test void invalidDetailsAndInternalPresenceFieldAreRejected(){
        Account a=h.account(true);int n=0;
        for(Map<String,Object> bad:List.of(Map.<String,Object>of("longitude","104"),Map.<String,Object>of("longitude","181","latitude","30"),Map.<String,Object>of("recommendedDays",31),Map.<String,Object>of("ticketInfo","未带来源的价格"),Map.<String,Object>of("officialUrl","javascript:alert(1)"),Map.<String,Object>of("providedDetails",List.of("address"))))
            error(h.json("POST","/api/v1/admin/destinations",a.token(),input("bad"+n++,bad)),400,40001);
    }

    @Test void galleryAttributionOrderAndOmissionAreSafe(){
        Account a=h.account(true);JsonNode d=create(a,"gallery",Map.of("images",List.of(attribution())));
        assertThat(d.path("images").size()).isEqualTo(1);
        assertThat(d.path("images").get(0).path("author").asText()).isEqualTo("Synthetic Author");
        assertThat(d.path("images").get(0).path("sortOrder").asInt()).isZero();
        JsonNode same=success(h.json("PUT","/api/v1/admin/destinations/"+d.path("id").asText(),a.token(),full(d)),200);
        assertThat(same.path("images").size()).isEqualTo(1);
        Map<String,Object> clear=full(same);clear.put("images",List.of());
        assertThat(success(h.json("PUT","/api/v1/admin/destinations/"+d.path("id").asText(),a.token(),clear),200).path("images").size()).isZero();
    }

    @Test void missingImageCannotCreateDestinationAndNonAdminCannotWrite(){
        Account a=h.account(true);Map<String,Object> image=new LinkedHashMap<>(attribution());image.put("url",IMAGE.replace("e".repeat(32),"d".repeat(32)));
        error(h.json("POST","/api/v1/admin/destinations",a.token(),input("missingImage",Map.of("images",List.of(image)))),400,40001);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM destinations WHERE name=?",Long.class,h.named("missingImage"))).isZero();
        error(h.json("POST","/api/v1/admin/destinations",h.account(false).token(),input("forbidden",Map.of("images",List.of(attribution())))),403,40301);
    }
}
