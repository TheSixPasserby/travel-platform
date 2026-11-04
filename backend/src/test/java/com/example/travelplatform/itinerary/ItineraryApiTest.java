package com.example.travelplatform.itinerary;

import com.example.travelplatform.testsupport.HttpTestSupport;
import com.example.travelplatform.testsupport.HttpTestSupport.Account;
import com.example.travelplatform.testsupport.HttpTestSupport.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static com.example.travelplatform.testsupport.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ItineraryApiTest {
    @LocalServerPort int port;
    @Autowired DataSource source;
    private HttpTestSupport h;
    @BeforeEach void setup(){h=new HttpTestSupport(source,port,"m5t");}
    @AfterEach void cleanup(){if(h!=null){try{h.cleanupAccounts();}finally{h.cleanupDestinations();}}}
    private Result call(String method,String path,Account a,Object body){return h.json(method,path,a==null?null:a.token(),body);}
    private JsonNode get(String path,Account a){return success(call("GET",path,a,null),200);}
    private String root(JsonNode t){return "/api/v1/itineraries/"+t.path("id").asText();}
    private String day(JsonNode d){return "/api/v1/itinerary-days/"+d.path("id").asText();}
    private String item(JsonNode i){return "/api/v1/itinerary-items/"+i.path("id").asText();}
    private Map<String,Object> body(String suffix,Map<String,?> extra){
        Map<String,Object>b=new LinkedHashMap<>(Map.of("title",h.named(suffix),"startDate","2026-11-03","endDate","2026-11-09"));b.putAll(extra);return b;
    }
    private JsonNode create(Account a,String suffix){return success(call("POST","/api/v1/itineraries",a,body(suffix,Map.of())),201);}
    private JsonNode addDay(Account a,JsonNode t,String date){return success(call("POST",root(t)+"/days",a,Map.of("travelDate",date)),201);}
    private JsonNode addItem(Account a,JsonNode d,String name){return success(call("POST",day(d)+"/items",a,Map.of("placeName",h.named(name))),201);}
    private Map<String,Object> replacement(JsonNode t,Map<String,?> extra){
        Map<String,Object>b=new LinkedHashMap<>();for(String k:List.of("title","startDate","endDate","status","notes"))b.put(k,t.path(k).isNull()?null:t.path(k).asText());
        b.put("destinationId",t.path("destination").isNull()?null:t.path("destination").path("id").asText());b.putAll(extra);return b;
    }
    private List<String> ids(JsonNode array){List<String>out=new ArrayList<>();array.forEach(x->out.add(x.path("id").asText()));return out;}
    private JsonNode listed(Account a,String query){return get("/api/v1/itineraries?keyword="+URLEncoder.encode(h.prefix(),StandardCharsets.UTF_8)+query,a);}
    private void allDenied(Account a,JsonNode t,JsonNode d,JsonNode i,int status,int code){
        error(call("GET",root(t),a,null),status,code);error(call("PUT",root(t),a,replacement(t,Map.of())),status,code);error(call("DELETE",root(t),a,null),status,code);
        error(call("POST",root(t)+"/days",a,Map.of("travelDate","2026-11-08")),status,code);
        error(call("PUT",day(d),a,Map.of("travelDate","2026-11-03")),status,code);error(call("DELETE",day(d),a,null),status,code);
        error(call("POST",day(d)+"/items",a,Map.of("placeName","无越权")),status,code);
        error(call("PUT",item(i),a,Map.of("placeName","无越权")),status,code);error(call("DELETE",item(i),a,null),status,code);
        error(call("PUT",day(d)+"/items/order",a,Map.of("itemIds",List.of(i.path("id").asText()))),status,code);
    }

    @Test void allEntrypointsRequireAnActiveSession(){
        error(call("GET","/api/v1/itineraries",null,null),401,40101);
        error(call("POST","/api/v1/itineraries",null,body("anon",Map.of())),401,40101);
        Account a=h.account(false);JsonNode t=create(a,"auth"),d=addDay(a,t,"2026-11-03"),i=addItem(a,d,"auth");
        allDenied(null,t,d,i,401,40101);
        h.jdbc().update("UPDATE users SET status='DISABLED' WHERE id=? AND username=?",a.id(),a.username());
        allDenied(a,t,d,i,401,40101);error(call("GET","/api/v1/itineraries",a,null),401,40101);
    }
    @Test void foreignUsersAndAdminsCannotReadOrMutateAnyLevel(){
        Account a=h.account(false),b=h.account(false),admin=h.account(true);JsonNode t=create(a,"owner"),d=addDay(a,t,"2026-11-03"),i=addItem(a,d,"owner");
        for(Account other:List.of(b,admin)){allDenied(other,t,d,i,403,40301);assertThat(listed(other,"&userId="+a.id()).path("total").asInt()).isZero();}
        assertThat(get(root(t),a).path("itemCount").asInt()).isEqualTo(1);
    }
    @Test void projectionsKeepIdsStringsTimesExplicitAndInternalFieldsPrivate(){
        Account a=h.account(false);JsonNode t=create(a,"projection");
        assertThat(t.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","title","destination","startDate","endDate","status","dayCount","itemCount","createdAt","updatedAt","notes","days");
        assertThat(t.path("id").isTextual()).isTrue();assertThat(t.path("createdAt").asText()).matches(".*\\.\\d{3}\\+08:00");
        assertThat(t.path("startDate").asText()).isEqualTo("2026-11-03");assertThat(t.path("days").isEmpty()).isTrue();
        JsonNode d=addDay(a,t,"2026-11-05");assertThat(d.path("itineraryId")).isEqualTo(t.path("id"));assertThat(d.path("dayNo").asInt()).isEqualTo(3);
        JsonNode i=success(call("POST",day(d)+"/items",a,Map.of("placeName","　山谷😀　","startTime","09:00","endTime","10:30","notes"," <script>test</script>\r\n徒步 ")),201);
        assertThat(i.propertyStream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","itineraryDayId","destination","placeName","addressText","startTime","endTime","notes","sortOrder","createdAt","updatedAt");
        assertThat(i.path("placeName").asText()).isEqualTo("山谷😀");assertThat(i.path("notes").asText()).isEqualTo("<script>test</script>\n徒步");assertThat(i.path("startTime").asText()).isEqualTo("09:00");
        JsonNode summary=listed(a,"").path("records").get(0);assertThat(summary.size()).isEqualTo(10);assertThat(summary.has("notes")).isFalse();assertThat(summary.has("userId")).isFalse();assertThat(summary.path("itemCount").asInt()).isEqualTo(1);
    }
    @Test void malformedInputsNeverCreateRowsOrApplyPartialChanges(){
        Account a=h.account(false);JsonNode t=create(a,"validation"),d=addDay(a,t,"2026-11-03");
        for(Map<String,?> invalid:List.of(Map.of("startDate","2023-02-29"),Map.of("startDate","2026-11-10"),Map.of("endDate","2027-11-04"),Map.of("startDate",20261103),Map.of("title","😀".repeat(161)),Map.of("title","\u0085"),Map.of("userId",a.id()),Map.of("destinationId",3),Map.of("status","DRAFT")))error(call("POST","/api/v1/itineraries",a,body("bad",invalid)),400,40001);
        Map<String,Object> nullStatus=body("null",Map.of());nullStatus.put("status",null);error(call("POST","/api/v1/itineraries",a,nullStatus),400,40001);
        Map<String,Object> noStatus=replacement(t,Map.of());noStatus.remove("status");error(call("PUT",root(t),a,noStatus),400,40001);
        error(call("PUT",day(d),a,Map.of("travelDate","2026-11-03","dayNo",7)),400,40001);
        for(Map<String,?> invalid:List.of(Map.of("placeName","😀".repeat(161)),Map.of("placeName","地点","endTime","24:00"),Map.of("placeName","地点","startTime","12:00","endTime","11:00"),Map.of("placeName","地点","sortOrder",2),Map.of("placeName","地点","addressText","a".repeat(301)),Map.of("placeName","地点","notes","a".repeat(2001))))error(call("POST",day(d)+"/items",a,invalid),400,40001);
        JsonNode after=get(root(t),a);assertThat(after.path("itemCount").asInt()).isZero();assertThat(after.path("title")).isEqualTo(t.path("title"));assertThat(listed(a,"").path("total").asInt()).isEqualTo(1);
        for(String query:List.of("page=0","size=51","status=HIDDEN","page=9223372036854775807&size=50"))error(call("GET","/api/v1/itineraries?"+query,a,null),400,40001);
    }
    @Test void listUsesPrivateStableDatabasePaginationStatusAndLiteralKeyword(){
        Account a=h.account(false);JsonNode x=create(a,"literal%_!\\"),y=create(a,"second"),z=create(a,"third");
        success(call("PUT",root(y),a,replacement(y,Map.of("status","ARCHIVED"))),200);
        h.jdbc().update("UPDATE itineraries SET created_at='2026-11-01 00:00:00.000' WHERE user_id=?",a.id());
        assertThat(listed(a,"&size=1").path("records").get(0).path("id")).isEqualTo(z.path("id"));assertThat(listed(a,"&size=1&page=2").path("records").get(0).path("id")).isEqualTo(y.path("id"));
        assertThat(listed(a,"&status=ARCHIVED").path("total").asInt()).isEqualTo(1);
        JsonNode beyond=listed(a,"&page=999&size=1");assertThat(beyond.path("records").isEmpty()).isTrue();assertThat(beyond.path("current").asInt()).isEqualTo(999);assertThat(beyond.path("total").asInt()).isEqualTo(3);
        String literal=URLEncoder.encode(x.path("title").asText(),StandardCharsets.UTF_8);assertThat(get("/api/v1/itineraries?keyword="+literal,a).path("total").asInt()).isEqualTo(1);
        JsonNode archived=success(call("PUT",root(y),a,replacement(y,Map.of("notes","归档仍可编辑"))),200);assertThat(archived.path("notes").asText()).isEqualTo("归档仍可编辑");
    }
    @Test void rootStartChangesRenumberAdjacentDaysWithoutMovingDatesAndShrinkIsAtomic(){
        Account a=h.account(false);JsonNode t=create(a,"renumber"),d1=addDay(a,t,"2026-11-04"),d2=addDay(a,t,"2026-11-05");addItem(a,d1,"keep");
        JsonNode earlier=success(call("PUT",root(t),a,replacement(t,Map.of("startDate","2026-11-02"))),200);
        assertThat(earlier.path("days").get(0).path("dayNo").asInt()).isEqualTo(3);assertThat(earlier.path("days").get(1).path("dayNo").asInt()).isEqualTo(4);
        JsonNode later=success(call("PUT",root(t),a,replacement(earlier,Map.of("startDate","2026-11-04"))),200);
        assertThat(later.path("days").get(0).path("dayNo").asInt()).isEqualTo(1);assertThat(later.path("days").get(1).path("dayNo").asInt()).isEqualTo(2);
        assertThat(later.path("days").get(0).path("travelDate").asText()).isEqualTo("2026-11-04");assertThat(later.path("itemCount").asInt()).isEqualTo(1);
        error(call("PUT",root(t),a,replacement(later,Map.of("endDate","2026-11-04","title",h.named("must-not-apply")))),409,40902);
        assertThat(get(root(t),a)).isEqualTo(later);
        error(call("PUT",day(d2),a,Map.of("travelDate","2026-11-04")),409,40901);
        JsonNode moved=success(call("PUT",day(d1),a,Map.of("travelDate","2026-11-06")),200);assertThat(moved.path("dayNo").asInt()).isEqualTo(3);assertThat(moved.path("items").size()).isEqualTo(1);
    }
    @Test void daysCannotEscapeDateRangeOrDuplicateAndDoNotAutofill(){
        Account a=h.account(false);JsonNode t=create(a,"days"),d=addDay(a,t,"2026-11-05");
        error(call("POST",root(t)+"/days",a,Map.of("travelDate","2026-11-05")),409,40901);
        error(call("POST",root(t)+"/days",a,Map.of("travelDate","2026-11-10")),400,40001);
        error(call("PUT",day(d),a,Map.of("travelDate","2026-11-02")),400,40001);
        JsonNode current=get(root(t),a);assertThat(current.path("days").size()).isEqualTo(1);assertThat(current.path("days").get(0).path("dayNo").asInt()).isEqualTo(3);
    }
    @Test void projectEditsReplaceOptionalFieldsAndKeepParentAndOrder(){
        Account a=h.account(false);JsonNode t=create(a,"edit"),d=addDay(a,t,"2026-11-03");
        JsonNode i=success(call("POST",day(d)+"/items",a,Map.of("placeName","原地点","addressText","旧地址","startTime","09:00","endTime","10:00","notes","旧备注")),201);
        JsonNode updated=success(call("PUT",item(i),a,Map.of("placeName","😀".repeat(160))),200);
        for(String optional:List.of("addressText","startTime","endTime","notes","destination"))assertThat(updated.path(optional).isNull()).isTrue();
        assertThat(updated.path("itineraryDayId")).isEqualTo(d.path("id"));assertThat(updated.path("sortOrder").asInt()).isZero();
    }
    @Test void orderRequiresTheEntireSameDayPermutationAndFailuresLeaveItUnchanged(){
        Account a=h.account(false);JsonNode t=create(a,"order"),d=addDay(a,t,"2026-11-03"),other=addDay(a,t,"2026-11-04");
        JsonNode x=addItem(a,d,"x"),y=addItem(a,d,"y"),z=addItem(a,d,"z"),cross=addItem(a,other,"cross");
        List<String> reversed=List.of(z.path("id").asText(),y.path("id").asText(),x.path("id").asText());
        JsonNode ordered=success(call("PUT",day(d)+"/items/order",a,Map.of("itemIds",reversed)),200);assertThat(ids(ordered.path("items"))).isEqualTo(reversed);
        for(int n=0;n<3;n++)assertThat(ordered.path("items").get(n).path("sortOrder").asInt()).isEqualTo(n);
        for(List<String> invalid:List.of(List.<String>of(),List.of(x.path("id").asText()),List.of(x.path("id").asText(),"0"+x.path("id").asText(),z.path("id").asText()),List.of(x.path("id").asText(),y.path("id").asText(),cross.path("id").asText())))error(call("PUT",day(d)+"/items/order",a,Map.of("itemIds",invalid)),400,40001);
        assertThat(get(root(t),a).path("days").get(0)).isEqualTo(ordered);
    }
    @Test void explicitChildDeletionCascadesOnlyItsOwnChildrenAndRootDeletionKeepsButHidesChildren(){
        Account a=h.account(false);JsonNode t=create(a,"delete"),d=addDay(a,t,"2026-11-03"),d2=addDay(a,t,"2026-11-04");
        JsonNode x=addItem(a,d,"x"),y=addItem(a,d,"y"),z=addItem(a,d2,"z");
        success(call("DELETE",item(x),a,null),200);error(call("DELETE",item(x),a,null),404,40401);
        JsonNode current=get(root(t),a);assertThat(current.path("days").get(0).path("items").get(0).path("sortOrder").asInt()).isZero();
        success(call("DELETE",day(d),a,null),200);assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_items WHERE id=?",Integer.class,y.path("id").asText())).isZero();
        assertThat(get(root(t),a).path("itemCount").asInt()).isEqualTo(1);
        success(call("DELETE",root(t),a,null),200);allDenied(a,t,d2,z,404,40401);
        assertThat(h.jdbc().queryForObject("SELECT COUNT(*) FROM itinerary_days WHERE id=?",Integer.class,d2.path("id").asText())).isEqualTo(1);assertThat(listed(a,"").path("total").asInt()).isZero();
    }
    @Test void inactiveDestinationReferencesAreHistoricalAndNewAssociationsAreRejected(){
        Account a=h.account(false),admin=h.account(true);
        JsonNode destination=success(call("POST","/api/v1/admin/destinations",admin,Map.of("name",h.named("destination"))),201);String dest=destination.path("id").asText();
        JsonNode t=success(call("POST","/api/v1/itineraries",a,body("dest",Map.of("destinationId",dest))),201),d=addDay(a,t,"2026-11-03");
        JsonNode i=success(call("POST",day(d)+"/items",a,Map.of("placeName","历史地点","destinationId",dest)),201);
        success(call("PATCH","/api/v1/admin/destinations/"+dest+"/status",admin,Map.of("status","INACTIVE")),200);
        assertThat(success(call("PUT",root(t),a,replacement(t,Map.of("notes","保留历史"))),200).path("destination").path("status").asText()).isEqualTo("INACTIVE");
        assertThat(success(call("PUT",item(i),a,Map.of("placeName","历史地点修改","destinationId",dest)),200).path("destination").path("status").asText()).isEqualTo("INACTIVE");
        error(call("POST","/api/v1/itineraries",a,body("new-inactive",Map.of("destinationId",dest))),400,40001);error(call("POST",day(d)+"/items",a,Map.of("placeName","新地点","destinationId",dest)),400,40001);
    }
    @Test void aDayWaitingOnTheRootLockSeesCommittedDateShrink() throws Exception {
        Account a=h.account(false);JsonNode t=create(a,"linearized");ExecutorService executor=Executors.newSingleThreadExecutor();
        try(var connection=source.getConnection()){
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("UPDATE itineraries SET end_date='2026-11-03' WHERE id=? AND user_id=?")){statement.setLong(1,Long.parseLong(t.path("id").asText()));statement.setLong(2,Long.parseLong(a.id()));assertThat(statement.executeUpdate()).isEqualTo(1);}
            try{Future<Result> future=executor.submit(()->call("POST",root(t)+"/days",a,Map.of("travelDate","2026-11-04")));assertThatThrownBy(()->future.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);connection.commit();error(future.get(25,TimeUnit.SECONDS),400,40001);assertThat(get(root(t),a).path("dayCount").asInt()).isZero();}
            finally{connection.rollback();}
        }finally{executor.shutdownNow();}
    }
    @Test void childrenDeletedWhileWaitingDoNotReuseTheInitialMapperLookup() throws Exception {
        Account a=h.account(false);ExecutorService executor=Executors.newSingleThreadExecutor();
        try{
            for(boolean removeDay:List.of(true,false)){
                JsonNode t=create(a,"stale"+removeDay),d=addDay(a,t,"2026-11-03"),i=addItem(a,d,"stale");
                try(var connection=source.getConnection()){
                    connection.setAutoCommit(false);
                    try(var lock=connection.prepareStatement("SELECT id FROM itineraries WHERE id=? FOR UPDATE")){
                        lock.setLong(1,Long.parseLong(t.path("id").asText()));try(var found=lock.executeQuery()){assertThat(found.next()).isTrue();}
                    }
                    String sql=removeDay?"DELETE FROM itinerary_days WHERE id=? AND itinerary_id=?":"DELETE FROM itinerary_items WHERE id=? AND itinerary_day_id=?";
                    try(var change=connection.prepareStatement(sql)){change.setLong(1,Long.parseLong((removeDay?d:i).path("id").asText()));change.setLong(2,Long.parseLong((removeDay?t:d).path("id").asText()));assertThat(change.executeUpdate()).isEqualTo(1);}
                    try{
                        Future<Result> waiting=executor.submit(()->removeDay?call("POST",day(d)+"/items",a,Map.of("placeName","must-not-insert")):call("PUT",item(i),a,Map.of("placeName","must-not-update")));
                        assertThatThrownBy(()->waiting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                        connection.commit();error(waiting.get(25,TimeUnit.SECONDS),404,40401);
                        assertThat(get(root(t),a).path("itemCount").asInt()).isZero();
                    }finally{connection.rollback();}
                }
            }
        }finally{executor.shutdownNow();}
    }
    @Test void dailyAndWholeItineraryItemLimitsAreEnforcedBeforeWriting(){
        Account a=h.account(false);JsonNode t=create(a,"caps");List<JsonNode>days=new ArrayList<>();
        for(int n=3;n<=8;n++)days.add(addDay(a,t,"2026-11-0"+n));
        List<Object[]>rows=new ArrayList<>();for(int d=0;d<5;d++)for(int n=0;n<100;n++)rows.add(new Object[]{days.get(d).path("id").asText(),h.named("cap"+d+"x"+n),n});
        h.jdbc().batchUpdate("INSERT INTO itinerary_items(itinerary_day_id,place_name,sort_order) VALUES (?,?,?)",rows);
        error(call("POST",day(days.get(0))+"/items",a,Map.of("placeName","daily overflow")),409,40902);
        error(call("POST",day(days.get(5))+"/items",a,Map.of("placeName","total overflow")),409,40902);
        assertThat(get(root(t),a).path("itemCount").asInt()).isEqualTo(500);
    }
}
