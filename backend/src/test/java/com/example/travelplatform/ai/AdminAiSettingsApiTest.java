package com.example.travelplatform.ai;

import com.example.travelplatform.testsupport.HttpTestSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminAiSettingsApiTest {
    static final Path settingsFile;
    static {
        try { settingsFile=Files.createTempDirectory("travel-ai-admin-").resolve("settings.json"); }
        catch (java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("travel.ai.settings-file",settingsFile::toString);
    }
    @LocalServerPort int port;
    @Autowired DataSource source;
    HttpTestSupport h;
    @BeforeEach void setup() throws Exception {
        h=new HttpTestSupport(source,port,"m6a");
        // One test owns this application-context settings file and restores the disabled state.
    }
    @AfterEach void cleanup() { h.cleanupAccounts(); }
    @Test void onlyAdminCanPersistLiveSettingsAndNoResponseDisclosesKey() throws Exception {
        var admin=h.account(true);
        var user=h.account(false);
        String path="/api/v1/admin/ai-settings";
        assertThat(h.json("GET",path,null,null).status()).isEqualTo(401);
        assertThat(h.json("GET",path,user.token(),null).status()).isEqualTo(403);
        assertThat(h.json("PUT",path,user.token(),Map.of()).status()).isEqualTo(403);
        var before=success(h.json("GET",path,admin.token(),null),200);
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("version",before.path("version").asLong());
        body.put("enabled",true);
        body.put("provider","custom");
        body.put("baseUrl","https://example.org/v1");
        body.put("model","test-model");
        body.put("apiKey","SYNTHETIC_ADMIN_KEY_58261");
        body.put("clearApiKey",false);
        body.put("responseFormat","json_object");
        body.put("connectTimeoutSeconds",5);
        body.put("timeoutSeconds",45);
        body.put("maxOutputTokens",6000);
        var saved=success(h.json("PUT",path,admin.token(),body),200);
        assertThat(saved.path("apiKeyConfigured").asBoolean()).isTrue();
        assertThat(saved.has("apiKey")).isFalse();
        assertThat(saved.toString()).doesNotContain("SYNTHETIC_ADMIN_KEY_58261");
        assertThat(Files.readString(settingsFile)).contains("SYNTHETIC_ADMIN_KEY_58261");
        var caps=success(h.json("GET","/api/v1/ai/itineraries/capabilities",user.token(),null),200);
        assertThat(caps.path("available").asBoolean()).isTrue();
        assertThat(caps.toString()).doesNotContain("example.org","SYNTHETIC_ADMIN_KEY_58261");
        assertThat(h.json("PUT",path,admin.token(),body).status()).isEqualTo(409);
        body.put("version",saved.path("version").asLong());
        body.put("apiKey","");
        body.put("model","second-model");
        saved=success(h.json("PUT",path,admin.token(),body),200);
        assertThat(saved.path("apiKeyConfigured").asBoolean()).isTrue();
        body.put("version",saved.path("version").asLong());
        body.put("baseUrl","https://another.example.org/v1");
        assertThat(h.json("PUT",path,admin.token(),body).status()).isEqualTo(400);
        body.put("enabled",false);
        body.put("clearApiKey",true);
        var disabled=success(h.json("PUT",path,admin.token(),body),200);
        assertThat(disabled.path("apiKeyConfigured").asBoolean()).isFalse();
        assertThat(Files.readString(settingsFile)).doesNotContain("SYNTHETIC_ADMIN_KEY_58261");
        assertThat(success(h.json("GET","/api/v1/ai/itineraries/capabilities",user.token(),null),200).path("available").asBoolean()).isFalse();
    }
}
