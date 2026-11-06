package com.example.travelplatform.ai.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.travelplatform.ai.mapper.AiCatalogMapper;
import com.example.travelplatform.ai.validation.AiInputPolicy.Input;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.destination.mapper.DestinationMapper;
import com.example.travelplatform.guide.mapper.GuideMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AiCatalog {
    private final DestinationMapper destinations;
    private final AiCatalogMapper sources;
    private final GuideMapper guides;

    public AiCatalog(DestinationMapper destinations, AiCatalogMapper sources, GuideMapper guides) {
        this.destinations=destinations;
        this.sources=sources;
        this.guides=guides;
    }

    public record Snapshot(Destination city, Map<Long,Destination> candidates,
                           List<AiCatalogMapper.GuideSource> guides, Map<Long,List<String>> tags) { }
    public record Refreshed(Destination city, Map<Long,Destination> candidates) { }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot read(Input input, Set<Long> used) {
        Destination city=destinations.selectById(input.cityId());
        if (!active(city) || !"CITY".equals(city.getDestinationType())) throw insufficient();
        Set<Long> requested=new LinkedHashSet<>(input.preferred());
        requested.addAll(input.must());
        requested.addAll(input.avoid());
        requested.addAll(used);
        Map<Long,Destination> references=requested.isEmpty() ? Map.of() : byId(destinations.selectByIds(requested));
        for (long id : requested) {
            if (!scenic(references.get(id),input.cityId())) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        if (!Collections.disjoint(used,input.avoid())) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        LinkedHashMap<Long,Destination> selected=new LinkedHashMap<>();
        Set<Long> preferred=new LinkedHashSet<>(input.must());
        preferred.addAll(input.preferred());
        preferred.removeAll(used);
        for (long id : preferred) selected.put(id,references.get(id));
        if (selected.size() > 40) throw new ApiException(ErrorCode.VALIDATION_FAILED);
        // At most 20 avoid + 60 used + 40 preferred can precede the next 40 eligible records.
        List<Destination> choices=destinations.selectList(Wrappers.<Destination>lambdaQuery()
                .eq(Destination::getParentId,input.cityId()).eq(Destination::getDestinationType,"SCENIC_SPOT")
                .eq(Destination::getStatus,"ACTIVE").orderByAsc(Destination::getSortOrder,Destination::getId)
                .last("LIMIT 160"));
        for (Destination place : choices) {
            if (selected.size() == 40) break;
            if (!input.avoid().contains(place.getId()) && !used.contains(place.getId())) selected.putIfAbsent(place.getId(),place);
        }
        if (selected.isEmpty()) throw insufficient();
        List<AiCatalogMapper.GuideSource> published=sources.publishedGuides(input.cityId());
        Map<Long,List<String>> tags=published.isEmpty() ? Map.of() : guides.tagsFor(published.stream().map(AiCatalogMapper.GuideSource::id).toList())
                .stream().collect(Collectors.groupingBy(row -> row.guideId(),Collectors.mapping(row -> row.name(),Collectors.toList())));
        return new Snapshot(city,Collections.unmodifiableMap(selected),List.copyOf(published),tags);
    }

    /** A fresh short read after the network call. No transaction spans model waiting. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Refreshed recheck(Input input, Collection<Long> ids) {
        Destination city=destinations.selectById(input.cityId());
        if (!active(city) || !"CITY".equals(city.getDestinationType())) throw insufficient();
        Map<Long,Destination> current=byId(destinations.selectByIds(ids));
        if (current.size() != ids.size() || current.values().stream().anyMatch(place -> !scenic(place,input.cityId()))) throw insufficient();
        return new Refreshed(city,current);
    }

    private static Map<Long,Destination> byId(List<Destination> values) {
        return values.stream().collect(Collectors.toMap(Destination::getId,Function.identity()));
    }
    private static boolean scenic(Destination place, long city) {
        return active(place) && "SCENIC_SPOT".equals(place.getDestinationType()) && Objects.equals(place.getParentId(),city);
    }
    private static boolean active(Destination place) {
        return place != null && Integer.valueOf(0).equals(place.getIsDeleted()) && "ACTIVE".equals(place.getStatus());
    }
    private static ApiException insufficient() { return new ApiException(ErrorCode.AI_DATA_INSUFFICIENT); }
}
