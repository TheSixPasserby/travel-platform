package com.example.travelplatform.ai;

import com.example.travelplatform.ai.dto.AiRequirements;
import com.example.travelplatform.ai.validation.AiInputPolicy;
import com.example.travelplatform.ai.validation.AiOutputPolicy;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.destination.entity.Destination;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiItineraryValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private AiRequirements request() {
        return new AiRequirements("1","2026-11-10","2026-11-11",2,"1000.00","TOTAL","CNY",
                List.of("CULTURE"),"BALANCED","PUBLIC",List.of("26"),List.of("27"),List.of());
    }
    private Map<Long,Destination> candidates() {
        Map<Long,Destination> places = new HashMap<>();
        for (long id : List.of(26L,27L)) {
            Destination spot = new Destination();
            spot.setId(id);spot.setName("database name "+id);spot.setAddress("database address "+id);
            spot.setParentId(1L);spot.setDestinationType("SCENIC_SPOT");spot.setStatus("ACTIVE");spot.setIsDeleted(0);
            places.put(id,spot);
        }
        return places;
    }
    private String valid() {
        return """
            {"title":"two days","summary":"a plan","days":[
            {"dayNo":1,"title":"first","items":[{"kind":"SCENIC_SPOT","destinationId":"26","startTime":"09:00","endTime":"10:30","notes":"estimate","estimatedCost":"10.00"}]},
            {"dayNo":2,"title":"second","items":[{"kind":"SCENIC_SPOT","destinationId":"27","startTime":"10:00","endTime":"11:00","notes":null,"estimatedCost":"0"}]}],
            "budgetEstimate":{"currency":"CNY","perPerson":"100","notes":"estimate only"},"warnings":[]}
            """;
    }
    @Test void trustedNamesDatesAndAmountsAreDerivedAfterStrictValidation() {
        var result=AiOutputPolicy.parse(valid(),AiInputPolicy.normalize(request()),candidates(),null,Set.of());
        assertEquals("database name 26",result.days().getFirst().items().getFirst().placeName());
        assertEquals("2026-11-11",result.days().get(1).travelDate());
        assertEquals("200.00",result.budget().total());
    }
    @Test void malformedOrInjectedOutputIsRejected() throws Exception {
        List<String> invalid = new ArrayList<>();
        invalid.add(valid()+" {}");
        invalid.add(valid().replace("\"title\":\"two days\"","\"title\":\"first\",\"title\":\"second\""));
        invalid.add(valid().replace("\"summary\":\"a plan\"","\"summary\":\"a plan\",\"userId\":\"14\""));
        invalid.add(valid().replace("\"destinationId\":\"26\"","\"destinationId\":26"));
        invalid.add(valid().replace("\"destinationId\":\"26\"","\"destinationId\":\"999\""));
        invalid.add(valid().replace("\"destinationId\":\"27\"","\"destinationId\":\"26\""));
        invalid.add(valid().replace("\"dayNo\":2","\"dayNo\":1"));
        invalid.add(valid().replace("\"09:00\"","\"25:00\""));
        invalid.add(valid().replace("\"100\"","\"-1\""));
        invalid.add(valid().replace("SCENIC_SPOT","FREE_TIME"));
        invalid.add("```json\n"+valid()+"\n```");
        for(String output:invalid) assertThrows(ApiException.class,()->AiOutputPolicy.parse(output,AiInputPolicy.normalize(request()),candidates(),null,Set.of()));
    }
    @Test void incompleteDaysOverlappingTimesAndMissingMustVisitAreRejected() throws Exception {
        ObjectNode root=(ObjectNode)json.readTree(valid());
        root.withArray("days").remove(1);
        assertThrows(ApiException.class,()->AiOutputPolicy.parse(root.toString(),AiInputPolicy.normalize(request()),candidates(),null,Set.of()));
        ObjectNode overlap=(ObjectNode)json.readTree(valid());
        var items=(com.fasterxml.jackson.databind.node.ArrayNode)overlap.withArray("days").get(0).withArray("items");
        items.add(json.readTree("{\"kind\":\"FREE_TIME\",\"destinationId\":null,\"startTime\":\"10:00\",\"endTime\":\"11:00\",\"notes\":\"rest\",\"estimatedCost\":\"0\"}"));
        assertThrows(ApiException.class,()->AiOutputPolicy.parse(overlap.toString(),AiInputPolicy.normalize(request()),candidates(),null,Set.of()));
    }
    @Test void requestRangesAndPreferenceConflictsAreStrict() {
        AiRequirements r=request();
        assertThrows(ApiException.class,()->AiInputPolicy.normalize(new AiRequirements(r.cityId(),r.startDate(),"2026-12-10",r.travelers(),r.budgetAmount(),r.budgetBasis(),r.currency(),r.interests(),r.pace(),r.transport(),r.preferredIds(),r.mustVisitIds(),r.avoidIds())));
        assertThrows(ApiException.class,()->AiInputPolicy.normalize(new AiRequirements(r.cityId(),r.startDate(),r.endDate(),r.travelers(),r.budgetAmount(),r.budgetBasis(),r.currency(),r.interests(),r.pace(),r.transport(),r.preferredIds(),r.mustVisitIds(),List.of("26"))));
    }
    @Test void infeasibleMustVisitCountIsRejectedBeforeAnyModelCall() {
        AiRequirements r=request();
        List<String> must=java.util.stream.LongStream.rangeClosed(1,9).mapToObj(Long::toString).toList();
        assertThrows(ApiException.class,()->AiInputPolicy.normalize(new AiRequirements(
                r.cityId(),r.startDate(),r.startDate(),r.travelers(),r.budgetAmount(),r.budgetBasis(),r.currency(),
                r.interests(),r.pace(),r.transport(),List.of(),must,List.of())));
    }
    @Test void singleDayMustNotReuseOtherDaysOrReturnExtraDays() throws Exception {
        ObjectNode root=(ObjectNode)json.readTree(valid());root.withArray("days").remove(0);
        var result=AiOutputPolicy.parse(root.toString(),AiInputPolicy.normalize(request()),candidates(),2,Set.of(26L));
        assertEquals(1,result.days().size());assertEquals(2,result.days().getFirst().dayNo());
        assertThrows(ApiException.class,()->AiOutputPolicy.parse(valid(),AiInputPolicy.normalize(request()),candidates(),2,Set.of(26L)));
    }
}
