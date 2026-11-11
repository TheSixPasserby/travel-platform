package com.example.travelplatform.ai;

import com.example.travelplatform.ai.client.OpenAiCompatibleClient;
import com.example.travelplatform.ai.config.*;
import com.example.travelplatform.ai.dto.AiSettingsRequest;
import com.example.travelplatform.common.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AiSettingsStoreTest {
    @TempDir Path directory;
    private final ObjectMapper json=new ObjectMapper();
    private AiProperties defaults() { return new AiProperties(false,"custom","","","",5,45,6000,"auto"); }
    private AiSettingsStore store() { return new AiSettingsStore(defaults(),directory.resolve("settings.json").toString(),json); }
    private AiSettingsRequest request(long version,boolean enabled,String url,String key) {
        return new AiSettingsRequest(version,enabled,"custom",url,"test-model",key,false,"json_object",5,45,6000);
    }
    @Test void savesRestartSafelyAndInvalidOrStaleEditsPreserveTheExistingConfiguration() throws Exception {
        var store=store();
        var saved=store.save(request(0,true,"https://example.org/v1","SYNTHETIC_PRIVATE_KEY"));
        assertThat(saved.apiKeyConfigured()).isTrue();
        var restarted=store();
        assertThat(restarted.current().apiKey()).isEqualTo("SYNTHETIC_PRIVATE_KEY");
        assertThat(restarted.view().version()).isEqualTo(1);
        byte[] before=Files.readAllBytes(directory.resolve("settings.json"));
        for (String url:List.of("file:///etc/passwd","https://user:password@example.org","https://example.org?api_key=x")) {
            assertThatThrownBy(() -> restarted.save(request(1,true,url,"SYNTHETIC_PRIVATE_KEY"))).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> restarted.save(request(0,true,"https://example.org/v1","OTHER_KEY"))).isInstanceOf(ApiException.class);
        assertThat(Files.readAllBytes(directory.resolve("settings.json"))).isEqualTo(before);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(directory.resolve("settings.json")))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
    }
    @Test void aCorruptFileDisablesAiAndLetsAnAdministratorRecover() throws Exception {
        Files.writeString(directory.resolve("settings.json"),"invalid JSON");
        var store=store();
        assertThat(store.current().available()).isFalse();
        assertThat(store.view().warning()).isNotBlank();
        store.save(request(0,false,"https://example.org/v1",""));
        assertThat(store.view().warning()).isNull();
        assertThat(store().view().version()).isEqualTo(1);
    }
    @Test void concurrentUpdatesAcceptOneRevisionAndRejectTheStaleOne() throws Exception {
        var store=store();
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> save=() -> {
                try { store.save(request(0,false,"https://example.org/v1",""));return 200; }
                catch (ApiException error) { return error.getErrorCode().getStatus().value(); }
            };
            var first=pool.submit(save);
            var second=pool.submit(save);
            assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,409);
            assertThat(store().view().version()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void failedPersistencePreservesTheActiveConfiguration() throws Exception {
        Path blocked = Files.createDirectory(directory.resolve("settings.json"));
        Files.writeString(blocked.resolve("keep.txt"), "existing content");
        var store = store();
        assertThatThrownBy(() -> store.save(request(0, true, "https://example.org/v1", "TEST_KEY")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(store.view().version()).isZero();
        assertThat(store.current().available()).isFalse();
        assertThat(Files.readString(blocked.resolve("keep.txt"))).isEqualTo("existing content");
    }

    @Test void existingClientUsesTheNewModelAndKeyOnTheNextActualHttpCall() throws Exception {
        var authorization=new AtomicReference<String>();
        var model=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            model.set(json.readTree(exchange.getRequestBody()).path("model").asText());
            byte[] response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            var store=store();
            store.save(request(0,true,url,"SYNTHETIC_FIRST_KEY"));
            var client=new OpenAiCompatibleClient(store);
            assertThat(client.generate("json")).isEqualTo("{}");
            assertThat(authorization.get()).isEqualTo("Bearer SYNTHETIC_FIRST_KEY");
            store.save(new AiSettingsRequest(1L,true,"custom",url,"updated-model","SYNTHETIC_SECOND_KEY",
                    false,"json_object",5,45,6000));
            assertThat(client.generate("json")).isEqualTo("{}");
            assertThat(authorization.get()).isEqualTo("Bearer SYNTHETIC_SECOND_KEY");
            assertThat(model.get()).isEqualTo("updated-model");
        } finally { server.stop(0); }
    }
}
