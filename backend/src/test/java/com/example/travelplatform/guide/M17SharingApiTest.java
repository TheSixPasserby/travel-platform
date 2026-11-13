package com.example.travelplatform.guide;
import com.example.travelplatform.testsupport.HttpTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="travel.site.public-base-url=https://travel.example")
@ActiveProfiles("test")
class M17SharingApiTest {
    @LocalServerPort int port;@Autowired DataSource source;HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m3g");}
    @AfterEach void cleanup(){try{h.cleanupGuides();h.cleanupDestinations();}finally{h.cleanupAccounts();}}
    @Test void shareHtmlIsServerRenderedEscapedAndUsesConfiguredCanonicalOrigin() {
        var a=h.account(false);
        String id=success(h.json("POST","/api/v1/guides",a.token(),Map.of("title","旅途 <script>alert(1)</script>","summary","绿野 & 山川","content","正文","status","PUBLISHED")),201).path("id").asText();
        var result=h.request("GET","/share/guides/"+id,null,null,Map.of("X-Forwarded-Host","attacker.invalid"));
        assertThat(result.status()).isEqualTo(200);
        String html=new String(result.bytes(),StandardCharsets.UTF_8);
        assertThat(html).contains("property=\"og:title\"","rel=\"canonical\"","https://travel.example/share/guides/"+id,"旅途 &lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)","attacker.invalid");
        assertThat(h.request("GET","/share/users/"+a.username(),null,null,Map.of()).status()).isEqualTo(200);
        String xml=new String(h.request("GET","/sitemap.xml",null,null,Map.of()).bytes(),StandardCharsets.UTF_8);
        assertThat(xml).contains("https://travel.example/share/guides/"+id);
        h.jdbc().update("UPDATE guides SET status='DRAFT' WHERE id=?",id);
        assertThat(h.request("GET","/share/guides/"+id,null,null,Map.of()).status()).isEqualTo(404);
        assertThat(new String(h.request("GET","/sitemap.xml",null,null,Map.of()).bytes(),StandardCharsets.UTF_8)).doesNotContain("/guides/"+id+"</loc>");
    }
    @Test void creatorDestinationsAreDerivedOnlyFromPublishedWorkAndValidRelations() {
        var a=h.account(false);String name=h.named("城市");
        h.jdbc().update("INSERT INTO destinations(name,destination_type) VALUES (?,'CITY')",name);
        long city=h.jdbc().queryForObject("SELECT id FROM destinations WHERE name=?",Long.class,name);
        String id=success(h.json("POST","/api/v1/guides",a.token(),Map.of("title","城市手记","destinationId",Long.toString(city),"content","正文","status","PUBLISHED")),201).path("id").asText();
        String path="/api/v1/users/by-username/"+a.username()+"/destinations";
        var list=success(h.json("GET",path,null,null),200);
        assertThat(list.path("total").asLong()).isEqualTo(1);
        assertThat(list.path("records").get(0).path("guideCount").asLong()).isEqualTo(1);
        h.jdbc().update("UPDATE guides SET status='DRAFT' WHERE id=?",id);
        assertThat(success(h.json("GET",path,null,null),200).path("records")).isEmpty();
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=?",a.id());
        error(h.json("GET",path,null,null),404,40401);
        assertThat(h.request("GET","/share/users/"+a.username(),null,null,Map.of()).status()).isEqualTo(404);
    }
}
