package com.example.travelplatform.user;

import com.example.travelplatform.testsupport.HttpTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.Map;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class M10IdentityApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m3g");}
    @AfterEach void cleanup(){try{h.cleanupGuides();}finally{h.cleanupAccounts();}}
    @Test void publicGuideAndCommentExposeOnlySafeIdentityAndNullableRegion(){
        var a=h.account(false);
        var g=success(h.json("POST","/api/v1/guides",a.token(),Map.of("title",h.named("identity"),"content","一段旅行记录","status","PUBLISHED")),201);
        var author=g.path("author");
        assertThat(author.path("username").asText()).isEqualTo(a.username());
        assertThat(author.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","nickname","username","avatarUrl","bio");
        assertThat(g.has("ipRegion")).isTrue();assertThat(g.path("ipRegion").isNull()).isTrue();
        var c=success(h.json("POST","/api/v1/guides/"+g.path("id").asText()+"/comments",a.token(),Map.of("content","安全留言")),201);
        assertThat(c.path("author").path("username").asText()).isEqualTo(a.username());
        assertThat(c.has("ipRegion")).isTrue();assertThat(c.path("ipRegion").isNull()).isTrue();
    }
}
