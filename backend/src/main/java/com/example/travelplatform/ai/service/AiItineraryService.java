package com.example.travelplatform.ai.service;

import com.example.travelplatform.ai.client.AiModelClient;
import com.example.travelplatform.ai.config.AiProperties;
import com.example.travelplatform.ai.dto.*;
import com.example.travelplatform.ai.validation.*;
import com.example.travelplatform.ai.vo.AiPreview;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import com.example.travelplatform.itinerary.vo.ItineraryDestination;
import com.example.travelplatform.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AiItineraryService {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");
    private final AiProperties properties;
    private final UserService session;
    private final AiCatalog catalog;
    private final AiModelClient client;
    private final AiGenerationGate gate;
    private final ObjectMapper json;

    public AiItineraryService(AiProperties properties, UserService session, AiCatalog catalog,
                              AiModelClient client, AiGenerationGate gate, ObjectMapper json) {
        this.properties=properties;
        this.session=session;
        this.catalog=catalog;
        this.client=client;
        this.gate=gate;
        this.json=json;
    }

    public AiPreview.Capabilities capabilities() {
        session.requireCurrentUser();
        var configuration=properties.resolved();
        String reason=properties.available() ? null : "AI服务暂未配置或启用，可继续使用手动行程";
        return new AiPreview.Capabilities(properties.enabled(),properties.available(),reason,14,
                configuration.provider(),configuration.label());
    }

    public AiPreview preview(AiRequirements request) {
        long user=ready();
        var input=AiInputPolicy.normalize(request);
        var snapshot=catalog.read(input,Set.of());
        var result=generate(user,input,snapshot,null,Set.of());
        return new AiPreview(result.plan().title(),result.plan().summary(),
                new ItineraryDestination(result.current().city().getId().toString(),result.current().city().getName(),"ACTIVE"),
                input.start().toString(),input.end().toString(),input.travelers(),input.budgetAmount().toPlainString(),input.budgetBasis(),"CNY",
                result.plan().days(),result.plan().budget(),warnings(result.plan(),input,snapshot,Set.of()),
                snapshot.candidates().keySet().stream().map(id -> candidate(result.current().candidates().get(id))).toList(),
                snapshot.guides().stream().map(source -> new AiPreview.SourceGuide(source.id().toString(),source.title())).toList());
    }

    public AiPreview.DayPreview day(AiDayRequest request) {
        long user=ready();
        if (request == null) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        var input=AiInputPolicy.normalize(request.requirements());
        int day=ItineraryDates.dayNo(input.start(),input.end(),ItineraryDates.date(request.targetDate()));
        Set<Long> used=AiInputPolicy.ids(request.usedDestinationIds(),60);
        var snapshot=catalog.read(input,used);
        var result=generate(user,input,snapshot,day,used);
        return new AiPreview.DayPreview(result.plan().days().getFirst(),warnings(result.plan(),input,snapshot,used));
    }

    private long ready() {
        long user=session.requireCurrentUser().getId();
        if (!properties.available()) throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        return user;
    }

    private record Generated(AiOutputPolicy.Validated plan, AiCatalog.Refreshed current) { }

    private Generated generate(long user, AiInputPolicy.Input input, AiCatalog.Snapshot snapshot,
                               Integer day, Set<Long> used) {
        try (var permit=gate.acquire(user)) {
            String output=client.generate(context(input,snapshot,day,used));
            var current=catalog.recheck(input,snapshot.candidates().keySet());
            return new Generated(AiOutputPolicy.parse(output,input,current.candidates(),day,used),current);
        } catch (AiGenerationGate.Rejected exception) {
            throw new ApiException(ErrorCode.AI_RATE_LIMIT);
        }
    }

    private String context(AiInputPolicy.Input input, AiCatalog.Snapshot snapshot, Integer day, Set<Long> used) {
        int detailLimit=180;
        int excerptLimit=1600;
        while (true) {
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("task",day == null ? "FULL_PLAN" : "SINGLE_DAY");
            payload.put("targetDayNo",day);
            payload.put("requirements",new AiRequirements(Long.toString(input.cityId()),input.start().toString(),input.end().toString(),
                    input.travelers(),input.budgetAmount().toPlainString(),input.budgetBasis(),"CNY",input.interests(),input.pace(),input.transport(),
                    input.preferred().stream().sorted().map(String::valueOf).toList(),input.must().stream().sorted().map(String::valueOf).toList(),
                    input.avoid().stream().sorted().map(String::valueOf).toList()));
            payload.put("otherDaysUsedIds",used.stream().sorted().map(String::valueOf).toList());
            payload.put("UNTRUSTED_DATA",data(snapshot,detailLimit,excerptLimit));
            try {
                String encoded=json.writeValueAsString(payload);
                // Reserve space for system instructions, JSON schema, format metadata and escaping.
                if (encoded.length() <= 14000) return encoded;
            } catch (JsonProcessingException exception) {
                throw new ApiException(ErrorCode.AI_DATA_INSUFFICIENT);
            }
            if (detailLimit > 0) detailLimit=detailLimit < 45 ? 0 : detailLimit/2;
            else if (excerptLimit > 0) excerptLimit=excerptLimit < 200 ? 0 : excerptLimit/2;
            else throw new ApiException(ErrorCode.AI_DATA_INSUFFICIENT);
        }
    }

    private Map<String,Object> data(AiCatalog.Snapshot snapshot, int detailLimit, int excerptLimit) {
        List<Map<String,Object>> places=new ArrayList<>();
        for (Destination place : snapshot.candidates().values()) {
            Map<String,Object> value=new LinkedHashMap<>();
            value.put("id",place.getId().toString());
            value.put("name",place.getName());
            value.put("description",clip(place.getDescription(),detailLimit));
            value.put("address",clip(place.getAddress(),detailLimit));
            value.put("longitude",place.getLongitude());
            value.put("latitude",place.getLatitude());
            value.put("recommendedDurationMinutes",place.getRecommendedDurationMinutes());
            value.put("recommendedSeasons",clip(place.getRecommendedSeasons(),detailLimit));
            value.put("openingHours",clip(place.getOpeningHours(),detailLimit));
            value.put("ticketInfo",clip(place.getTicketInfo(),detailLimit));
            value.put("verifiedAt",place.getVerifiedAt());
            value.put("hasSource",place.getSourceUrl()!=null);
            places.add(value);
        }
        List<Map<String,Object>> guides=new ArrayList<>();
        for (var source : snapshot.guides()) {
            Map<String,Object> value=new LinkedHashMap<>();
            value.put("id",source.id().toString());
            value.put("title",source.title());
            value.put("excerpt",clip(source.content(),excerptLimit));
            value.put("tags",snapshot.tags().getOrDefault(source.id(),List.of()));
            guides.add(value);
        }
        return Map.of("city",Map.of("id",snapshot.city().getId().toString(),"name",snapshot.city().getName()),
                "candidateScenicSpots",places,"publishedGuides",guides,
                "textMayBeClipped",detailLimit<180 || excerptLimit<1600);
    }

    private static List<String> warnings(AiOutputPolicy.Validated result, AiInputPolicy.Input input,
                                         AiCatalog.Snapshot snapshot, Set<Long> used) {
        LinkedHashSet<String> warnings=new LinkedHashSet<>(result.warnings());
        Set<Long> visited=new HashSet<>(used);
        for (var day : result.days()) {
            for (var item : day.items()) if (item.destinationId()!=null) visited.add(Long.parseLong(item.destinationId()));
        }
        for (long id : input.preferred()) {
            if (!visited.contains(id)) warnings.add("本次未安排想去的景点，请按需要调整："+snapshot.candidates().get(id).getName());
        }
        BigDecimal estimated="TOTAL".equals(input.budgetBasis())
                ? new BigDecimal(result.budget().total()) : new BigDecimal(result.budget().perPerson());
        if (estimated.compareTo(input.budgetAmount())>0) warnings.add("当前费用估算高于填写的预算，请调整项目和预算");
        warnings.add("费用均为估算，出发前请核对天气、开放时间、预约、票务和交通。缺少资料的项目以官方公告为准。");
        return List.copyOf(warnings);
    }

    private static AiPreview.Candidate candidate(Destination place) {
        String verified=place.getVerifiedAt()==null ? null : TIMESTAMP.format(place.getVerifiedAt().atOffset(ZoneOffset.ofHours(8)));
        return new AiPreview.Candidate(place.getId().toString(),place.getName(),place.getAddress(),place.getCoverImageUrl(),
                place.getRecommendedDurationMinutes(),place.getOpeningHours(),place.getTicketInfo(),place.getSourceUrl(),verified);
    }

    private static String clip(String value, int limit) {
        if (value==null || limit==0) return null;
        return value.codePointCount(0,value.length())<=limit ? value : value.substring(0,value.offsetByCodePoints(0,limit));
    }
}
