package com.example.travelplatform.ai;

import com.example.travelplatform.ai.client.OpenAiCompatibleClient;
import com.example.travelplatform.ai.config.AiProperties;
import com.example.travelplatform.ai.config.AiSettingsStore;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import com.example.travelplatform.common.error.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AiModelClientTest {
    @TempDir Path settingsDirectory;
    private HttpServer server;
    private ExecutorService executor;
    private final AtomicInteger calls=new AtomicInteger();
    private final AtomicInteger status=new AtomicInteger(200);
    private final AtomicInteger delayMillis=new AtomicInteger();
    private final AtomicReference<String> body=new AtomicReference<>();
    private final AtomicReference<String> received=new AtomicReference<>();
    private final ObjectMapper json=new ObjectMapper();
    private String base;

    @BeforeEach void start() throws Exception {
        body.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/chat/completions",exchange->{
            calls.incrementAndGet();received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            if (status.get()==302) exchange.getResponseHeaders().set("Location",base+"/leak");
            byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(),bytes.length);
            try {
                if (delayMillis.get()>0) Thread.sleep(delayMillis.get());
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally {exchange.close();}
        });
        server.createContext("/leak",exchange->{calls.incrementAndGet();exchange.sendResponseHeaders(500,-1);exchange.close();});
        server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
    private AiProperties config(String provider,String format) {
        return new AiProperties(true,provider,base,"SYNTHETIC_QA_KEY","test-model",1,1,6000,format);
    }
    private AiSettingsStore store(AiProperties value) { return new AiSettingsStore(value,settingsDirectory.resolve("settings.json").toString(),json); }
    private OpenAiCompatibleClient client(){return new OpenAiCompatibleClient(store(config("custom","json_object")));}
    private void failure(ErrorCode expected){
        ApiException exception=assertThrows(ApiException.class,()->client().generate("json UNTRUSTED_DATA"));
        assertEquals(expected,exception.getErrorCode());assertFalse(exception.getMessage().contains("SYNTHETIC_QA_KEY"));
    }
    @Test void sendsBoundedCompatibleRequestWithoutAutomaticRetries() throws Exception {
        assertEquals("{}",client().generate("json UNTRUSTED_DATA"));assertEquals(1,calls.get());
        var request=json.readTree(received.get());assertEquals("json_object",request.path("response_format").path("type").asText());
        assertEquals(6000,request.path("max_tokens").asInt());assertFalse(received.get().contains("SYNTHETIC_QA_KEY"));
        assertTrue(request.path("messages").get(0).path("content").asText().contains("UNTRUSTED_DATA"));
    }
    @Test void strictPresetSendsSchemaAndOpenAiTokenParameter() throws Exception {
        new OpenAiCompatibleClient(store(config("openai","json_schema"))).generate("json UNTRUSTED_DATA");
        var request=json.readTree(received.get());assertEquals(6000,request.path("max_completion_tokens").asInt());
        assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean());
    }
    @Test void providerErrorsAreClassifiedAndNeverRetried(){
        for (var entry:Map.of(429,ErrorCode.AI_UPSTREAM_RATE_LIMIT,500,ErrorCode.AI_UPSTREAM_FAILED,401,ErrorCode.AI_UPSTREAM_FAILED,302,ErrorCode.AI_UPSTREAM_FAILED).entrySet()) {
            int before=calls.get();status.set(entry.getKey());failure(entry.getValue());assertEquals(before+1,calls.get());
        }
    }
    @Test void bodyReadTimeoutIsClassifiedAndMakesOneCall(){
        delayMillis.set(1800);failure(ErrorCode.AI_TIMEOUT);assertEquals(1,calls.get());
    }
    @Test void malformedEmptyTruncatedOrOversizedResponsesAreInvalidOutput(){
        for (String response:List.of("not-json","", "{}", "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"{}\"}}]}"," ".repeat(262145))) {
            body.set(response);failure(ErrorCode.AI_INVALID_OUTPUT);
        }
    }
    @Test void missingKeyAndOversizedContextDoNotMakeNetworkRequests(){
        var disabled=new AiProperties(true,"custom",base,"","test",1,1,6000,"auto");
        assertFalse(disabled.available());assertThrows(ApiException.class,()->new OpenAiCompatibleClient(store(disabled)).generate("json"));
        assertThrows(ApiException.class,()->client().generate("x".repeat(24001)));assertEquals(0,calls.get());
        assertFalse(config("custom","auto").toString().contains("SYNTHETIC_QA_KEY"));
    }
}
