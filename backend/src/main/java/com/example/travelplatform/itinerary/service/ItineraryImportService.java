package com.example.travelplatform.itinerary.service;

import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.destination.mapper.DestinationMapper;
import com.example.travelplatform.itinerary.dto.*;
import com.example.travelplatform.itinerary.mapper.ItineraryMapper;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import com.example.travelplatform.itinerary.validation.ItineraryText;
import com.example.travelplatform.itinerary.vo.ItineraryDetailVO;
import com.example.travelplatform.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItineraryImportService {
    private final ItineraryService service;
    private final ItineraryMapper roots;
    private final DestinationMapper destinations;
    private final UserService session;
    public ItineraryImportService(ItineraryService service, ItineraryMapper roots,
                                  DestinationMapper destinations, UserService session) {
        this.service = service;
        this.roots = roots;
        this.destinations = destinations;
        this.session = session;
    }
    public record Imported(ItineraryDetailVO itinerary, boolean created) { }

    /** One outer transaction encompasses all existing service calls; no partial roots/days/items. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Imported save(ItineraryImportRequest request) {
        long user = session.requireCurrentUser().getId();
        if (request == null || request.clientRequestId() == null
                || !request.clientRequestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw invalid();
        }
        String key = UUID.fromString(request.clientRequestId()).toString();
        // Per-user database lock serializes same-key submissions even across application instances.
        if (roots.lockImportUser(user) == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        var existing = roots.forRequest(user,key);
        if (existing != null) {
            if (!Integer.valueOf(0).equals(existing.getIsDeleted())) {
                throw new ApiException(ErrorCode.STATE_CONFLICT,"该请求已保存的行程已删除，请重新生成保存标识");
            }
            return new Imported(service.get(existing.getId().toString()),false);
        }
        long cityId = RequestValues.positiveId(request.destinationId());
        var range = ItineraryDates.range(request.startDate(),request.endDate());
        String title = text(request.title(),160,true);
        String notes = text(request.notes(),2000,false);
        if (request.days() == null || request.days().isEmpty() || request.days().size() > 366) {
            throw invalid();
        }
        Map<Long,Destination> graph = destinations.lockGraph().stream()
                .collect(Collectors.toMap(Destination::getId,Function.identity()));
        Destination city = graph.get(cityId);
        if (!active(city) || !"CITY".equals(city.getDestinationType())) {
            throw invalid();
        }
        Set<LocalDate> dates = new HashSet<>();
        Set<Long> scenicIds = new HashSet<>();
        List<DayValue> values = new ArrayList<>();
        int total = 0;
        for (var day : request.days()) {
            if (day == null || day.items() == null || day.items().size() > 100) {
                throw invalid();
            }
            LocalDate date = ItineraryDates.date(day.travelDate());
            ItineraryDates.dayNo(range.startDate(),range.endDate(),date);
            total += day.items().size();
            if (!dates.add(date) || total > 500) {
                throw invalid();
            }
            List<ItemWriteRequest> items = new ArrayList<>();
            LocalTime previousEnd = null;
            for (var item : day.items()) {
                if (item == null || item.kind() == null) {
                    throw invalid();
                }
                String name;
                String address;
                String id;
                if ("SCENIC_SPOT".equals(item.kind())) {
                    long scenicId = RequestValues.positiveId(item.destinationId());
                    Destination spot = graph.get(scenicId);
                    if (!active(spot) || !"SCENIC_SPOT".equals(spot.getDestinationType())
                            || !Objects.equals(spot.getParentId(),cityId) || !scenicIds.add(scenicId)) {
                        throw invalid();
                    }
                    name = spot.getName();
                    address = spot.getAddress();
                    id = Long.toString(scenicId);
                } else if ("FREE_TIME".equals(item.kind())) {
                    if (item.destinationId() != null) {
                        throw invalid();
                    }
                    name = "自由活动";
                    address = null;
                    id = null;
                } else {
                    throw invalid();
                }
                // Validate ignored names too; caller-provided model text cannot bypass length/control limits.
                text(item.placeName(),160,false);
                text(item.addressText(),300,false);
                var times = ItineraryDates.times(item.startTime(),item.endTime());
                if (previousEnd != null && times.startTime() != null && times.startTime().isBefore(previousEnd)) {
                    throw invalid();
                }
                if (times.endTime() != null) {
                    previousEnd = times.endTime();
                }
                items.add(new ItemWriteRequest(text(name,160,true),id,text(address,300,false),
                        item.startTime(),item.endTime(),text(item.notes(),2000,false)));
            }
            values.add(new DayValue(date,text(day.title(),160,false),text(day.notes(),2000,false),items));
        }
        var create = new ItineraryCreateRequest();
        create.setTitle(title);
        create.setDestinationId(Long.toString(cityId));
        create.setStartDate(request.startDate());
        create.setEndDate(request.endDate());
        create.setNotes(notes);
        ItineraryDetailVO root = service.create(create);
        if (roots.importKey(Long.parseLong(root.id()),key,user) != 1) {
            throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        values.sort(Comparator.comparing(DayValue::date));
        for (var value : values) {
            var day = service.createDay(root.id(),new DayWriteRequest(value.date().toString(),value.title(),value.notes()));
            for (var item : value.items()) {
                service.createItem(day.id(),item);
            }
        }
        return new Imported(service.get(root.id()),true);
    }
    private record DayValue(LocalDate date, String title, String notes, List<ItemWriteRequest> items) { }

    private static String text(String raw, int max, boolean required) {
        return ItineraryText.normalize(raw,max,required);
    }

    private static boolean active(Destination destination) {
        return destination != null && Integer.valueOf(0).equals(destination.getIsDeleted())
                && "ACTIVE".equals(destination.getStatus());
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED,"整程资料、日期或景点不合法");
    }
}
