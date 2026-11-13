package com.example.travelplatform.itinerary.service;

import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.guide.entity.Guide;
import com.example.travelplatform.guide.mapper.*;
import com.example.travelplatform.guide.service.FeedCursor;
import com.example.travelplatform.itinerary.dto.*;
import com.example.travelplatform.itinerary.entity.Itinerary;
import com.example.travelplatform.itinerary.mapper.*;
import com.example.travelplatform.itinerary.vo.GuideTripImportResult;
import com.example.travelplatform.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
public class GuideTripImportService {
    private final ItineraryMapper roots;
    private final DayMapper days;
    private final GuideMapper guides;
    private final GuideDestinationsMapper links;
    private final GuideTripImportMapper receipts;
    private final ItineraryService itineraries;
    private final UserService users;
    private final ObjectMapper json;

    public GuideTripImportService(ItineraryMapper roots, DayMapper days, GuideMapper guides,
            GuideDestinationsMapper links, GuideTripImportMapper receipts, ItineraryService itineraries,
            UserService users, ObjectMapper json) {
        this.roots = roots; this.days = days; this.guides = guides; this.links = links; this.receipts = receipts;
        this.itineraries = itineraries; this.users = users; this.json = json;
    }

    /** User-row serialization and the unique receipt key work across application instances. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GuideTripImportResult save(GuideTripImportRequest request) {
        long user = users.requireCurrentUser().getId();
        if (request == null || request.clientRequestId() == null
                || !request.clientRequestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || request.destinationIds() == null || request.destinationIds().isEmpty() || request.destinationIds().size() > 20) throw invalid();
        boolean creating = request.newItinerary() != null;
        if (creating ? request.itineraryId() != null || request.dayId() != null : request.itineraryId() == null || request.dayId() == null) throw invalid();
        var selected = request.destinationIds().stream().map(RequestValues::positiveId).toList();
        if (new HashSet<>(selected).size() != selected.size()) throw invalid();
        String key = UUID.fromString(request.clientRequestId()).toString();
        String hash = FeedCursor.fingerprint(serialize(List.of(request.guideId() == null ? "" : request.guideId(), selected,
                request.itineraryId() == null ? "" : request.itineraryId(), request.dayId() == null ? "" : request.dayId(),
                creating ? request.newItinerary() : "")));
        if (roots.lockImportUser(user) == null) throw new ApiException(ErrorCode.UNAUTHORIZED);
        var receipt = receipts.find(user, key);
        if (receipt != null) {
            if (!receipt.requestHash().equals(hash)) throw conflict("该提交标识已用于另一组地点，请重新确认");
            // A receipt records a committed action. Later target edits do not execute that action again.
            try { return json.readValue(receipt.responseJson(), GuideTripImportResult.class); }
            catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
        }
        Itinerary target = creating ? null : requireTarget(RequestValues.positiveId(request.itineraryId()), RequestValues.positiveId(request.dayId()), user);
        Guide guide = guides.lock(RequestValues.positiveId(request.guideId()));
        if (guide == null || guide.getIsDeleted() != 0 || !"PUBLISHED".equals(guide.getStatus())) throw new ApiException(ErrorCode.NOT_FOUND);
        // Public author validation and the guide lock prevent hidden/deleted source races.
        users.publicUserByUsername(users.publicProfile(guide.getAuthorId().toString()).username());
        var linked = new HashSet<>(links.ids(guide.getId()));
        if (!linked.containsAll(selected) || guide.getDestinationId() == null) throw invalid();
        Destination city = roots.lockDestination(guide.getDestinationId());
        if (!active(city) || !"CITY".equals(city.getDestinationType())) throw invalid();
        var places = new ArrayList<Destination>();
        // Lock in ID order, preserve the user's chosen order when constructing items.
        var byId = new HashMap<Long, Destination>();
        for (long id : selected.stream().sorted().toList()) {
            Destination place = roots.lockDestination(id);
            if (!active(place) || !"SCENIC_SPOT".equals(place.getDestinationType()) || !Objects.equals(place.getParentId(), city.getId())) throw invalid();
            byId.put(id, place);
        }
        for (long id : selected) places.add(byId.get(id));
        GuideTripImportResult result = creating ? create(request, key, user, city, places)
                : append(target, Long.toString(RequestValues.positiveId(request.dayId())), places);
        receipts.insert(user, key, hash, Long.parseLong(result.itineraryId()), Long.parseLong(result.dayId()), serialize(result));
        return result;
    }

    private GuideTripImportResult create(GuideTripImportRequest request, String key, long user, Destination city, List<Destination> places) {
        var value = request.newItinerary();
        if (roots.forRequest(user, key) != null) throw conflict("该标识已用于其他行程，请重新确认");
        var body = new ItineraryCreateRequest();
        body.setTitle(value.title()); body.setDestinationId(city.getId().toString());
        body.setStartDate(value.startDate()); body.setEndDate(value.endDate());
        var saved = itineraries.create(body);
        roots.importKey(Long.parseLong(saved.id()), key, user);
        var day = itineraries.createDay(saved.id(), new DayWriteRequest(value.travelDate(), null, null));
        for (var place : places) itineraries.createItem(day.id(), new ItemWriteRequest(place.getName(), place.getId().toString(), place.getAddress(), null, null, null));
        return new GuideTripImportResult(saved.id(), day.id(), places.stream().map(p -> p.getId().toString()).toList(), List.of());
    }

    private GuideTripImportResult append(Itinerary target, String dayId, List<Destination> places) {
        var detail = itineraries.get(target.getId().toString());
        var day = detail.days().stream().filter(d -> d.id().equals(dayId)).findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        var present = new HashSet<String>();
        day.items().stream().filter(item -> item.destination() != null).forEach(item -> present.add(item.destination().id()));
        var added = new ArrayList<String>(); var existing = new ArrayList<String>();
        for (var place : places) {
            String id = place.getId().toString();
            if (present.contains(id)) existing.add(id);
            else {
                itineraries.createItem(dayId, new ItemWriteRequest(place.getName(), id, place.getAddress(), null, null, null));
                added.add(id);
            }
        }
        return new GuideTripImportResult(target.getId().toString(), dayId, added, existing);
    }

    private Itinerary requireTarget(long rootId, long dayId, long user) {
        Itinerary target = roots.lock(rootId);
        if (target == null || target.getIsDeleted() != 0) throw new ApiException(ErrorCode.NOT_FOUND);
        if (!target.getUserId().equals(user)) throw new ApiException(ErrorCode.FORBIDDEN);
        if (!"ACTIVE".equals(target.getStatus())) throw conflict("已归档的行程不能加入地点");
        var day = days.find(dayId);
        if (day == null || !day.getItineraryId().equals(rootId)) throw new ApiException(ErrorCode.NOT_FOUND);
        return target;
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }
    private static boolean active(Destination place) { return place != null && place.getIsDeleted() == 0 && "ACTIVE".equals(place.getStatus()); }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED, "请选择攻略关联的有效景点及正确行程日期"); }
    private static ApiException conflict(String message) { return new ApiException(ErrorCode.STATE_CONFLICT, message); }
}
