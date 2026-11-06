package com.example.travelplatform.itinerary;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.example.travelplatform.testsupport.HttpTestSupport.Result;
import com.example.travelplatform.itinerary.entity.ItineraryDay;
import com.example.travelplatform.itinerary.mapper.DayMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ItineraryImportApiTest {
 @LocalServerPort int port; @Autowired DataSource source; @MockitoSpyBean DayMapper dayMapper; @Autowired org.mybatis.spring.SqlSessionTemplate sql;
 HttpTestSupport h;
 @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m5t");}
 @AfterEach void cleanup(){if(h!=null){try{h.cleanupAccounts();}finally{h.cleanupDestinations();}}}
 String destination(String name,String type,String parent){
  h.jdbc().update("INSERT INTO destinations(name,destination_type,parent_id,country,status,sort_order,is_deleted) VALUES(?,?,?,'China','ACTIVE',0,0)",h.named(name),type,parent);
  return h.jdbc().queryForObject("SELECT id FROM destinations WHERE name=?",String.class,h.named(name));
 }
 String city(){return destination("city","CITY",null);}
 Map<String,Object> item(String id){Map<String,Object> m=new LinkedHashMap<>();m.put("kind","SCENIC_SPOT");m.put("destinationId",id);m.put("placeName","fake model place");m.put("addressText","fake model address");m.put("startTime","09:00");m.put("endTime","10:30");m.put("notes","estimate only");return m;}
 Map<String,Object> free(){Map<String,Object> m=new LinkedHashMap<>();m.put("kind","FREE_TIME");m.put("destinationId",null);m.put("placeName","untrusted name");m.put("startTime","12:00");m.put("endTime","13:00");return m;}
 Map<String,Object> day(String date,List<?> items){return new LinkedHashMap<>(Map.of("travelDate",date,"title","a day","items",items));}
 Map<String,Object> body(String city,String spot){return new LinkedHashMap<>(Map.of("clientRequestId",UUID.randomUUID().toString(),"title",h.named("import"),"destinationId",city,"startDate","2026-11-10","endDate","2026-11-11","notes","budget estimate","days",List.of(day("2026-11-10",List.of(item(spot))),day("2026-11-11",List.of(free())))));}
 Result post(Account a,Object body){return h.json("POST","/api/v1/itineraries/import",a==null?null:a.token(),body);}
 int count(Account a){return h.jdbc().queryForObject("SELECT COUNT(*) FROM itineraries WHERE user_id=?",Integer.class,a.id());}
 @Test void atomicImportDerivesNamesAndPersistsOriginalShapeOwnedByActor(){
  Account a=h.account(false),b=h.account(false);String c=city(),s=destination("spot","SCENIC_SPOT",c);var body=body(c,s);
  error(post(null,body),401,40101);JsonNode root=success(post(a,body),201);
  assertThat(root.path("itemCount").asInt()).isEqualTo(2);assertThat(root.path("dayCount").asInt()).isEqualTo(2);
  assertThat(root.path("days").get(0).path("items").get(0).path("placeName").asText()).isEqualTo(h.named("spot"));
  assertThat(root.path("days").get(0).path("items").get(0).path("addressText").isNull()).isTrue();
  assertThat(root.path("days").get(1).path("items").get(0).path("destination").isNull()).isTrue();
  assertThat(root.path("days").get(1).path("items").get(0).path("placeName").asText()).isEqualTo("自由活动");
  assertThat(success(h.json("GET","/api/v1/itineraries/"+root.path("id").asText(),a.token(),null),200).path("days")).isEqualTo(root.path("days"));
  error(h.json("GET","/api/v1/itineraries/"+root.path("id").asText(),b.token(),null),403,40301);
 }
 @Test void sameKeyIsIdempotentAcrossConcurrentRequestsAndNeverResurrectsDeletedRoot() throws Exception {
  Account a=h.account(false),b=h.account(false);String c=city(),s=destination("spot","SCENIC_SPOT",c);var body=body(c,s);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   Callable<Result> call=()->{start.await();return post(a,body);};var f1=pool.submit(call);var f2=pool.submit(call);start.countDown();Result r1=f1.get(20,TimeUnit.SECONDS),r2=f2.get(20,TimeUnit.SECONDS);
   assertThat(List.of(r1.status(),r2.status())).containsExactlyInAnyOrder(200,201);assertThat(r1.json().path("data").path("id")).isEqualTo(r2.json().path("data").path("id"));assertThat(count(a)).isEqualTo(1);
   String id=r1.json().path("data").path("id").asText();success(post(b,body),201);assertThat(count(b)).isEqualTo(1);
   success(h.json("DELETE","/api/v1/itineraries/"+id,a.token(),null),200);error(post(a,body),409,40902);assertThat(count(a)).isEqualTo(1);
  } finally {pool.shutdownNow();}
 }
 @Test void allReferencesAndStructureValidateBeforeAnyBusinessRows(){
  Account a=h.account(false);String c=city(),s=destination("spot","SCENIC_SPOT",c),foreign=destination("othercity","CITY",null),fs=destination("foreign","SCENIC_SPOT",foreign);var valid=body(c,s);
  for(Map<String,?> extra:List.of(Map.of("destinationId",foreign),Map.of("days",List.of(day("2026-11-10",List.of(item(fs))))),Map.of("endDate","2028-11-10"),Map.of("clientRequestId","not-uuid"),Map.of("itineraryId","2"),Map.of("userId",a.id()),Map.of("notes","a".repeat(2001)),Map.of("days",List.of(day("2026-11-10",List.of(free())),day("2026-11-10",List.of(free())))),Map.of("days",List.of(day("2026-11-09",List.of(free())))))){
   var invalid=new LinkedHashMap<>(valid);invalid.putAll(extra);error(post(a,invalid),400,40001);assertThat(count(a)).isZero();
  }
  for(var bad:List.of(new LinkedHashMap<>(Map.of("kind","FREE_TIME","destinationId",s)),new LinkedHashMap<>(Map.of("kind","UNKNOWN","destinationId",s)),new LinkedHashMap<>(Map.of("kind","SCENIC_SPOT","destinationId",c)),new LinkedHashMap<>(Map.of("kind","SCENIC_SPOT","destinationId",s,"startTime","25:00")))){
   var invalid=new LinkedHashMap<>(valid);invalid.put("days",List.of(day("2026-11-10",List.of(bad))));error(post(a,invalid),400,40001);assertThat(count(a)).isZero();
  }
  h.jdbc().update("UPDATE destinations SET status='INACTIVE' WHERE id=?",s);error(post(a,valid),400,40001);assertThat(count(a)).isZero();
 }
 @Test void generalImportAllows366DaysAnd500ItemsButRejects501AndInvalidNestedOwnership(){
  Account a=h.account(false);String c=city(),s=destination("spot","SCENIC_SPOT",c);var valid=body(c,s);
  var longTrip=new LinkedHashMap<>(valid);longTrip.put("endDate","2027-11-10");success(post(a,longTrip),201);
  List<Map<String,Object>> manyDays=new ArrayList<>();
  for(int n=0;n<5;n++)manyDays.add(day("2026-11-"+(10+n),Collections.nCopies(100,Map.of("kind","FREE_TIME"))));
  var fiveHundred=new LinkedHashMap<>(valid);fiveHundred.put("clientRequestId",UUID.randomUUID().toString());fiveHundred.put("endDate","2026-11-15");fiveHundred.put("days",manyDays);
  JsonNode saved=success(post(a,fiveHundred),201);assertThat(saved.path("itemCount").asInt()).isEqualTo(500);
  manyDays.add(day("2026-11-15",List.of(Map.of("kind","FREE_TIME"))));fiveHundred.put("clientRequestId",UUID.randomUUID().toString());error(post(a,fiveHundred),400,40001);assertThat(count(a)).isEqualTo(2);
  var nested=new LinkedHashMap<>(valid);nested.put("clientRequestId",UUID.randomUUID().toString());var badItem=item(s);badItem.put("itineraryDayId","3");nested.put("days",List.of(day("2026-11-10",List.of(badItem))));error(post(a,nested),400,40001);
  var duplicate=new LinkedHashMap<>(valid);duplicate.put("clientRequestId",UUID.randomUUID().toString());duplicate.put("days",List.of(day("2026-11-10",List.of(item(s))),day("2026-11-11",List.of(item(s)))));error(post(a,duplicate),400,40001);
  h.jdbc().update("UPDATE destinations SET status='INACTIVE' WHERE id=?",c);var inactive=new LinkedHashMap<>(valid);inactive.put("clientRequestId",UUID.randomUUID().toString());error(post(a,inactive),400,40001);assertThat(count(a)).isEqualTo(2);
 }
 @Test void limitsAndSecondDayFailureLeaveNoRootDayOrItem(){
  Account a=h.account(false);String c=city(),s=destination("spot","SCENIC_SPOT",c);var valid=body(c,s);var tooMany=new LinkedHashMap<>(valid);tooMany.put("days",List.of(day("2026-11-10",Collections.nCopies(101,free()))));error(post(a,tooMany),400,40001);assertThat(count(a)).isZero();
  var counter=new AtomicInteger();doAnswer(invocation->{if(counter.incrementAndGet()==2)throw new IllegalStateException("synthetic failure");return sql.getMapper(DayMapper.class).insert((ItineraryDay)invocation.getArgument(0));}).when(dayMapper).insert(any(ItineraryDay.class));
  error(post(a,valid),500,50001);assertThat(counter.get()).isEqualTo(2);assertThat(count(a)).isZero();
  assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_days d JOIN itineraries i ON d.itinerary_id=i.id WHERE i.user_id=?",Integer.class,a.id())).isZero();
 }
}
