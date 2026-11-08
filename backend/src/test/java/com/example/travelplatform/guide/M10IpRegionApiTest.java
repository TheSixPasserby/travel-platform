package com.example.travelplatform.guide;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.example.travelplatform.common.ip.IpRegionResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="travel.ip.trusted-proxies=127.0.0.1,::1")
@ActiveProfiles("test")
class M10IpRegionApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    @MockitoBean IpRegionResolver regions;
    HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m3g");when(regions.resolve(any())).thenReturn("四川");}
    @AfterEach void cleanup(){try{h.cleanupGuides();}finally{h.cleanupAccounts();}}
    private String path(JsonNode guide){return "/api/v1/guides/"+guide.path("id").asText();}
    private HttpTestSupport.Result forwarded(String method,String path,Account account,String json){
        return h.request(method,path,account.token(),json,Map.of("X-Forwarded-For","1.1.1.1"));
    }
    private JsonNode create(Account a,String status){return success(forwarded("POST","/api/v1/guides",a,"{\"title\":\""+h.named("region")+"\",\"content\":\"旅行正文\",\"status\":\""+status+"\"}"),201);}
    @Test void firstPublicationSnapshotSurvivesEditsWithdrawalAndModeration(){
        var a=h.account(false);var admin=h.account(true);var g=create(a,"DRAFT");
        assertThat(g.path("ipRegion").isNull()).isTrue();verifyNoInteractions(regions);
        var published=success(forwarded("POST",path(g)+"/publish",a,null),200);
        assertThat(published.path("ipRegion").asText()).isEqualTo("四川");var time=published.path("publishedAt");
        when(regions.resolve(any())).thenReturn("浙江");
        var edited=success(h.json("PUT",path(g),a.token(),Map.of("title",h.named("edited"),"content","新的正文")),200);
        assertThat(edited.path("ipRegion").asText()).isEqualTo("四川");
        success(h.json("POST",path(g)+"/unpublish",a.token(),null),200);
        var republished=success(forwarded("POST",path(g)+"/publish",a,null),200);
        assertThat(republished.path("ipRegion").asText()).isEqualTo("四川");assertThat(republished.path("publishedAt")).isEqualTo(time);
        for(String status:List.of("HIDDEN","PUBLISHED"))success(h.json("PATCH","/api/v1/admin/guides/"+g.path("id").asText()+"/status",admin.token(),Map.of("status",status)),200);
        assertThat(success(h.json("GET",path(g),null,null),200).path("ipRegion").asText()).isEqualTo("四川");verify(regions,times(1)).resolve(any());
    }
    @Test void unknownAtFirstPublicationIsNeverBackfilledAndCommentsDegradeSafely(){
        var a=h.account(false);when(regions.resolve(any())).thenReturn(null);var g=create(a,"PUBLISHED");
        assertThat(g.path("ipRegion").isNull()).isTrue();
        success(h.json("POST",path(g)+"/unpublish",a.token(),null),200);when(regions.resolve(any())).thenReturn("浙江");
        assertThat(success(forwarded("POST",path(g)+"/publish",a,null),200).path("ipRegion").isNull()).isTrue();
        when(regions.resolve(any())).thenThrow(new IllegalStateException("synthetic provider outage"));
        var c=success(forwarded("POST",path(g)+"/comments",a,"{\"content\":\"不会因属地服务失败丢失评论\"}"),201);
        assertThat(c.path("ipRegion").isNull()).isTrue();
        doReturn("四川").when(regions).resolve(any());
        var known=success(forwarded("POST",path(g)+"/comments",a,"{\"content\":\"测试数据源的省级结果\"}"),201);
        assertThat(known.path("ipRegion").asText()).isEqualTo("四川");
        var rows=success(h.json("GET",path(g)+"/comments",null,null),200).path("records");assertThat(rows.size()).isEqualTo(2);
        assertThat(rows.get(0).path("ipRegion").asText()).isEqualTo("四川");
    }
    @Test void clientCannotWriteRegionOrRawIp(){
        var a=h.account(false);var g=create(a,"DRAFT");
        for(String field:List.of("ipRegion","ip","authorId")){
            error(forwarded("POST","/api/v1/guides",a,"{\"title\":\"bad\",\"content\":\"正文\",\""+field+"\":\"四川\"}"),400,40001);
            error(forwarded("PUT",path(g),a,"{\"title\":\"bad\",\"content\":\"正文\",\""+field+"\":\"四川\"}"),400,40001);
        }
        success(forwarded("POST",path(g)+"/publish",a,null),200);
        error(forwarded("POST",path(g)+"/comments",a,"{\"content\":\"bad\",\"ipRegion\":\"四川\"}"),400,40001);
    }
    @Test void concurrentPublishCapturesExactlyOneHistoricalSnapshot() throws Exception {
        var a=h.account(false);var g=create(a,"DRAFT");var pool=Executors.newFixedThreadPool(4);
        try {
            List<Future<JsonNode>> jobs=new ArrayList<>();
            for(int i=0;i<8;i++)jobs.add(pool.submit(()->success(forwarded("POST",path(g)+"/publish",a,null),200)));
            for(var job:jobs)assertThat(job.get(25,TimeUnit.SECONDS).path("ipRegion").asText()).isEqualTo("四川");
            verify(regions,times(1)).resolve(any());
        }finally{pool.shutdownNow();}
    }
}
