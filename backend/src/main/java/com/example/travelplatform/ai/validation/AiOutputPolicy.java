package com.example.travelplatform.ai.validation;

import com.example.travelplatform.ai.vo.AiPreview;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import com.example.travelplatform.itinerary.validation.ItineraryText;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;

public final class AiOutputPolicy {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private AiOutputPolicy() { }

    public record Validated(String title, String summary, List<AiPreview.Day> days,
                            AiPreview.Budget budget, List<String> warnings) { }

    public static Validated parse(String output, AiInputPolicy.Input input,
                                  Map<Long,Destination> candidates, Integer targetDay, Set<Long> used) {
        try {
            if (output == null || output.length() > 100_000) throw invalid();
            JsonNode root = JSON.readTree(output);
            exact(root,"title","summary","days","budgetEstimate","warnings");
            String title = text(root.get("title"),160,true);
            String summary = text(root.get("summary"),1000,true);
            JsonNode dayNodes = root.get("days");
            int expectedDays = targetDay == null ? input.dayCount() : 1;
            if (!dayNodes.isArray() || dayNodes.size() != expectedDays) throw invalid();
            List<AiPreview.Day> days = new ArrayList<>();
            Set<Integer> numbers = new HashSet<>();
            Set<Long> visited = new HashSet<>();
            BigDecimal costs = BigDecimal.ZERO;
            int itemCount = 0;
            for (JsonNode day : dayNodes) {
                exact(day,"dayNo","title","items");
                JsonNode number = day.get("dayNo");
                if (!number.isIntegralNumber() || !number.canConvertToInt()) throw invalid();
                int dayNo = number.intValue();
                if (dayNo < 1 || dayNo > input.dayCount() || !numbers.add(dayNo)
                        || (targetDay != null && dayNo != targetDay)) throw invalid();
                JsonNode itemNodes = day.get("items");
                if (!itemNodes.isArray() || itemNodes.isEmpty() || itemNodes.size() > 8) throw invalid();
                if ((itemCount += itemNodes.size()) > 60) throw invalid();
                List<AiPreview.Item> items = new ArrayList<>();
                java.time.LocalTime previousEnd = null;
                for (JsonNode item : itemNodes) {
                    exact(item,"kind","destinationId","startTime","endTime","notes","estimatedCost");
                    String kind = text(item.get("kind"),20,true);
                    String id = null;
                    String name;
                    String address = null;
                    if ("SCENIC_SPOT".equals(kind)) {
                        JsonNode idNode = item.get("destinationId");
                        if (!idNode.isTextual()) throw invalid();
                        long destinationId = RequestValues.positiveId(idNode.textValue());
                        Destination place = candidates.get(destinationId);
                        if (place == null || !Objects.equals(place.getParentId(),input.cityId())
                                || !"SCENIC_SPOT".equals(place.getDestinationType()) || !"ACTIVE".equals(place.getStatus())
                                || !Integer.valueOf(0).equals(place.getIsDeleted()) || !visited.add(destinationId)
                                || used.contains(destinationId) || input.avoid().contains(destinationId)) throw invalid();
                        id = Long.toString(destinationId);
                        name = place.getName();
                        address = place.getAddress();
                    } else if ("FREE_TIME".equals(kind)) {
                        if (!item.get("destinationId").isNull()) throw invalid();
                        name = "自由活动";
                    } else throw invalid();
                    String start = text(item.get("startTime"),5,true);
                    String end = text(item.get("endTime"),5,true);
                    var times = ItineraryDates.times(start,end);
                    if (!times.endTime().isAfter(times.startTime())
                            || (previousEnd != null && times.startTime().isBefore(previousEnd))) throw invalid();
                    previousEnd = times.endTime();
                    BigDecimal cost = amount(item.get("estimatedCost"));
                    costs = costs.add(cost);
                    items.add(new AiPreview.Item(kind,id,name,address,start,end,text(item.get("notes"),1000,false),cost.toPlainString()));
                }
                days.add(new AiPreview.Day(dayNo,input.start().plusDays(dayNo-1L).toString(),
                        text(day.get("title"),160,true),List.copyOf(items)));
            }
            Set<Long> required = new HashSet<>(input.must());
            required.removeAll(used);
            if (!visited.containsAll(required)) throw invalid();
            JsonNode budgetNode = root.get("budgetEstimate");
            exact(budgetNode,"currency","perPerson","notes");
            if (!"CNY".equals(text(budgetNode.get("currency"),3,true))) throw invalid();
            BigDecimal perPerson = amount(budgetNode.get("perPerson"));
            if (perPerson.compareTo(costs) < 0) throw invalid();
            AiPreview.Budget budget = new AiPreview.Budget("CNY",perPerson.toPlainString(),
                    perPerson.multiply(BigDecimal.valueOf(input.travelers())).toPlainString(),text(budgetNode.get("notes"),1000,true));
            JsonNode warningNodes = root.get("warnings");
            if (!warningNodes.isArray() || warningNodes.size() > 20) throw invalid();
            List<String> warnings = new ArrayList<>();
            for (JsonNode warning : warningNodes) warnings.add(text(warning,500,true));
            days.sort(Comparator.comparingInt(AiPreview.Day::dayNo));
            return new Validated(title,summary,List.copyOf(days),budget,List.copyOf(warnings));
        } catch (ApiException exception) {
            throw invalid();
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static void exact(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) throw invalid();
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(fields))) throw invalid();
    }

    private static String text(JsonNode node, int maximum, boolean required) {
        if (node == null || node.isNull()) {
            if (required) throw invalid();
            return null;
        }
        if (!node.isTextual()) throw invalid();
        return ItineraryText.normalize(node.textValue(),maximum,required);
    }

    private static BigDecimal amount(JsonNode node) {
        if (node == null || !node.isTextual()) throw invalid();
        return AiInputPolicy.amount(node.textValue());
    }

    private static ApiException invalid() { return new ApiException(ErrorCode.AI_INVALID_OUTPUT); }
}
