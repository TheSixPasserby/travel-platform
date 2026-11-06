package com.example.travelplatform.ai;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.util.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="travel.ai.enabled=false")
@ActiveProfiles("test")
class AiItineraryApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m5t");}
    @AfterEach void cleanup(){h.cleanupAccounts();}
    @Test void disabledAiIsExplicitAndPrivateAndManualCreationStillWorks(){
        error(h.json("GET","/api/v1/ai/itineraries/capabilities",null,null),401,40101);
        Account a=h.account(false);
        var capabilities=success(h.json("GET","/api/v1/ai/itineraries/capabilities",a.token(),null),200);
        assertThat(capabilities.path("available").asBoolean()).isFalse();
        assertThat(capabilities.has("apiKey")).isFalse();assertThat(capabilities.has("baseUrl")).isFalse();
        error(h.json("POST","/api/v1/ai/itineraries/preview",a.token(),Map.of()),503,50310);
        success(h.json("POST","/api/v1/itineraries",a.token(),Map.of("title",h.named("manual"),"startDate","2026-11-06","endDate","2026-11-06")),201);
    }
}
