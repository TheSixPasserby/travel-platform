package com.example.travelplatform.itinerary.service;

import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.destination.mapper.DestinationMapper;
import com.example.travelplatform.itinerary.dto.*;
import com.example.travelplatform.itinerary.entity.*;
import com.example.travelplatform.itinerary.mapper.*;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import com.example.travelplatform.itinerary.vo.*;
import com.example.travelplatform.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ItineraryService {
    private static final Set<String> STATUSES=Set.of("ACTIVE","ARCHIVED");
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT);
    private final ItineraryMapper roots;
    private final DayMapper days;
    private final ItemMapper items;
    private final DestinationMapper destinations;
    private final UserService session;
    public ItineraryService(ItineraryMapper roots,DayMapper days,ItemMapper items,DestinationMapper destinations,UserService session){
        this.roots=roots;this.days=days;this.items=items;this.destinations=destinations;this.session=session;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<ItinerarySummaryVO> list(String rawPage,String rawSize,String keyword,String status){
        long user=session.requireCurrentUser().getId(),page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        if(status!=null&&!STATUSES.contains(status))throw invalid();
        String word=RequestValues.keyword(keyword),pattern=word==null?null:RequestValues.likePattern(word);
        long total=roots.count(user,pattern,status);List<ItineraryRow> rows=offset>=total?List.of():roots.page(user,pattern,status,size,offset);
        Map<Long,Destination> refs=references(rows.stream().map(Itinerary::getDestinationId).filter(Objects::nonNull).toList());
        return new PageResponse<>(rows.stream().map(row->summary(row,refs)).toList(),total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ItineraryDetailVO get(String id){
        long user=session.requireCurrentUser().getId();ItineraryRow row=required(roots.detail(RequestValues.positiveId(id)));owned(row,user);return detail(row);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ItineraryDetailVO create(ItineraryCreateRequest request){
        long user=session.requireCurrentUser().getId();RootValues value=values(request);reference(value.destinationId(),null);
        Itinerary root=new Itinerary();root.setUserId(user);root.setIsDeleted(0);root.setCreatedAt(now());root.setUpdatedAt(root.getCreatedAt());
        apply(root,value);roots.insert(root);return detail(required(roots.detail(root.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ItineraryDetailVO update(String id,ItineraryUpdateRequest request){
        Itinerary root=lockOwned(RequestValues.positiveId(id));RootValues value=values(request);
        List<ItineraryDay> existing=days.forRoot(root.getId());
        for(ItineraryDay day:existing){
            if(day.getTravelDate().isBefore(value.range().startDate())||day.getTravelDate().isAfter(value.range().endDate()))throw conflict("新日期范围不能排除已有日程，请先调整对应日程");
            if(day.getDayNo()<1||day.getDayNo()>366)throw conflict("已有日程编号不符合范围，请检查日程");
        }
        reference(value.destinationId(),root.getDestinationId());
        LocalDate oldStart=root.getStartDate();apply(root,value);root.setUpdatedAt(now());changed(roots.change(root));
        if(!oldStart.equals(root.getStartDate())){
            // Stage into a disjoint 1001..1366 range before assigning the final unique day numbers.
            days.stageNumbers(root.getId());days.renumber(root.getId(),root.getStartDate(),root.getUpdatedAt());
        }
        return detail(required(roots.detail(root.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(String id){Itinerary root=lockOwned(RequestValues.positiveId(id));changed(roots.softDelete(root.getId(),now()));}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DayVO createDay(String id,DayWriteRequest request){
        Itinerary root=lockOwned(RequestValues.positiveId(id));ItineraryDay day=new ItineraryDay();day.setItineraryId(root.getId());
        apply(day,request,root);if(days.conflicts(root.getId(),day.getTravelDate(),null)>0)throw new ApiException(ErrorCode.DUPLICATE_KEY,"这一天已有安排");
        day.setCreatedAt(now());day.setUpdatedAt(day.getCreatedAt());days.insert(day);touch(root);return dayView(day);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DayVO updateDay(String id,DayWriteRequest request){
        OwnedDay locked=lockDay(RequestValues.positiveId(id));ItineraryDay day=locked.day();apply(day,request,locked.root());
        if(days.conflicts(locked.root().getId(),day.getTravelDate(),day.getId())>0)throw new ApiException(ErrorCode.DUPLICATE_KEY,"这一天已有安排");
        day.setUpdatedAt(now());changed(days.change(day));touch(locked.root());return dayView(day);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void deleteDay(String id){OwnedDay locked=lockDay(RequestValues.positiveId(id));changed(days.deleteById(locked.day().getId()));touch(locked.root());}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ItemVO createItem(String id,ItemWriteRequest request){
        OwnedDay locked=lockDay(RequestValues.positiveId(id));List<ItineraryItem> existing=items.forDay(locked.day().getId());
        if(existing.size()>=100||items.countRoot(locked.root().getId())>=500)throw conflict("每天最多100个项目，每个行程最多500个项目");
        ItineraryItem item=new ItineraryItem();apply(item,request);reference(item.getDestinationId(),null);
        item.setItineraryDayId(locked.day().getId());item.setSortOrder(existing.size());item.setCreatedAt(now());item.setUpdatedAt(item.getCreatedAt());
        items.insert(item);touch(locked.root());return itemView(item,references(single(item.getDestinationId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ItemVO updateItem(String id,ItemWriteRequest request){
        OwnedItem locked=lockItem(RequestValues.positiveId(id));ItineraryItem item=locked.item();Long original=item.getDestinationId();
        apply(item,request);reference(item.getDestinationId(),original);item.setUpdatedAt(now());changed(items.change(item));touch(locked.root());
        return itemView(item,references(single(item.getDestinationId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void deleteItem(String id){
        OwnedItem locked=lockItem(RequestValues.positiveId(id));changed(items.deleteById(locked.item().getId()));
        List<ItineraryItem> remaining=items.forDay(locked.day().getId());LocalDateTime time=now();
        for(int n=0;n<remaining.size();n++)if(remaining.get(n).getSortOrder()!=n)changed(items.position(remaining.get(n).getId(),locked.day().getId(),n,time));
        touch(locked.root());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DayVO order(String id,ItemOrderRequest request){
        OwnedDay locked=lockDay(RequestValues.positiveId(id));
        if(request==null||request.itemIds()==null||request.itemIds().size()>100)throw invalid();
        List<Long> requested=request.itemIds().stream().map(RequestValues::positiveId).toList();Set<Long> unique=new HashSet<>(requested);
        List<ItineraryItem> existing=items.forDay(locked.day().getId());Set<Long> actual=existing.stream().map(ItineraryItem::getId).collect(Collectors.toSet());
        if(unique.size()!=requested.size()||!unique.equals(actual))throw new ApiException(ErrorCode.VALIDATION_FAILED,"排序必须包含当天全部项目且不能重复");
        LocalDateTime time=now();for(int n=0;n<requested.size();n++)changed(items.position(requested.get(n),locked.day().getId(),n,time));
        touch(locked.root());return dayView(locked.day());
    }

    private Itinerary lockOwned(long id){long user=session.requireCurrentUser().getId();Itinerary root=required(roots.lock(id));owned(root,user);return root;}
    private OwnedDay lockDay(long id){
        ItineraryDay initial=days.find(id);if(initial==null)throw missing();
        Itinerary root=lockOwned(initial.getItineraryId());ItineraryDay current=days.find(id);
        if(current==null||!current.getItineraryId().equals(root.getId()))throw missing();return new OwnedDay(root,current);
    }
    private OwnedItem lockItem(long id){
        ItineraryItem initial=items.find(id);if(initial==null)throw missing();ItineraryDay parent=days.find(initial.getItineraryDayId());if(parent==null)throw missing();
        Itinerary root=lockOwned(parent.getItineraryId());ItineraryItem current=items.find(id);ItineraryDay currentDay=days.find(parent.getId());
        if(current==null||currentDay==null||!current.getItineraryDayId().equals(parent.getId())||!currentDay.getItineraryId().equals(root.getId()))throw missing();
        return new OwnedItem(root,currentDay,current);
    }
    private void reference(Long id,Long previous){
        if(id==null)return;Destination ref=roots.lockDestination(id);
        if(ref==null||!Integer.valueOf(0).equals(ref.getIsDeleted())||(!Objects.equals(id,previous)&&!"ACTIVE".equals(ref.getStatus())))throw new ApiException(ErrorCode.VALIDATION_FAILED,"请选择可用的目的地");
    }
    private static RootValues values(ItineraryUpdateRequest request){
        if(request==null||request.getStatus()==null||!STATUSES.contains(request.getStatus()))throw invalid();
        return new RootValues(text(request.getTitle(),160,true),optionalId(request.getDestinationId()),ItineraryDates.range(request.getStartDate(),request.getEndDate()),text(request.getNotes(),2000,false),request.getStatus());
    }
    private static void apply(Itinerary root,RootValues value){
        root.setTitle(value.title());root.setDestinationId(value.destinationId());root.setStartDate(value.range().startDate());root.setEndDate(value.range().endDate());root.setNotes(value.notes());root.setStatus(value.status());
    }
    private static void apply(ItineraryDay day,DayWriteRequest request,Itinerary root){
        if(request==null)throw invalid();LocalDate date=ItineraryDates.date(request.travelDate());int number=ItineraryDates.dayNo(root.getStartDate(),root.getEndDate(),date);
        day.setTravelDate(date);day.setDayNo(number);day.setSortOrder(number-1);day.setTitle(text(request.title(),160,false));day.setNotes(text(request.notes(),2000,false));
    }
    private static void apply(ItineraryItem item,ItemWriteRequest request){
        if(request==null)throw invalid();ItineraryDates.Times times=ItineraryDates.times(request.startTime(),request.endTime());
        item.setPlaceName(text(request.placeName(),160,true));item.setDestinationId(optionalId(request.destinationId()));item.setAddressText(text(request.addressText(),300,false));
        item.setStartTime(times.startTime());item.setEndTime(times.endTime());item.setNotes(text(request.notes(),2000,false));
    }
    private ItineraryDetailVO detail(ItineraryRow root){
        List<ItineraryDay> dayRows=days.forRoot(root.getId());List<ItineraryItem> itemRows=items.forRoot(root.getId());
        Set<Long> ids=new HashSet<>();if(root.getDestinationId()!=null)ids.add(root.getDestinationId());itemRows.stream().map(ItineraryItem::getDestinationId).filter(Objects::nonNull).forEach(ids::add);
        Map<Long,Destination> refs=references(ids);Map<Long,List<ItemVO>> grouped=new HashMap<>();
        for(ItineraryItem item:itemRows)grouped.computeIfAbsent(item.getItineraryDayId(),ignored->new ArrayList<>()).add(itemView(item,refs));
        List<DayVO> views=dayRows.stream().map(day->dayView(day,grouped.getOrDefault(day.getId(),List.of()))).toList();
        return ItineraryDetailVO.from(summary(root,refs),root.getNotes(),views);
    }
    private DayVO dayView(ItineraryDay day){
        List<ItineraryItem> rows=items.forDay(day.getId());Map<Long,Destination> refs=references(rows.stream().map(ItineraryItem::getDestinationId).filter(Objects::nonNull).toList());
        return dayView(day,rows.stream().map(item->itemView(item,refs)).toList());
    }
    private Map<Long,Destination> references(Collection<Long> ids){
        if(ids.isEmpty())return Map.of();return destinations.selectByIds(new HashSet<>(ids)).stream().collect(Collectors.toMap(Destination::getId,Function.identity()));
    }
    private static ItinerarySummaryVO summary(ItineraryRow row,Map<Long,Destination> refs){
        return new ItinerarySummaryVO(row.getId().toString(),row.getTitle(),ref(row.getDestinationId(),refs),row.getStartDate(),row.getEndDate(),row.getStatus(),row.getDayCount(),row.getItemCount(),offset(row.getCreatedAt()),offset(row.getUpdatedAt()));
    }
    private static DayVO dayView(ItineraryDay day,List<ItemVO> views){
        return new DayVO(day.getId().toString(),day.getItineraryId().toString(),day.getDayNo(),day.getTravelDate(),day.getTitle(),day.getNotes(),offset(day.getCreatedAt()),offset(day.getUpdatedAt()),views);
    }
    private static ItemVO itemView(ItineraryItem item,Map<Long,Destination> refs){
        return new ItemVO(item.getId().toString(),item.getItineraryDayId().toString(),ref(item.getDestinationId(),refs),item.getPlaceName(),item.getAddressText(),item.getStartTime()==null?null:TIME.format(item.getStartTime()),item.getEndTime()==null?null:TIME.format(item.getEndTime()),item.getNotes(),item.getSortOrder(),offset(item.getCreatedAt()),offset(item.getUpdatedAt()));
    }
    private static ItineraryDestination ref(Long id,Map<Long,Destination> refs){if(id==null)return null;Destination dest=refs.get(id);return dest==null?null:new ItineraryDestination(dest.getId().toString(),dest.getName(),dest.getStatus());}
    private static Collection<Long> single(Long id){return id==null?List.of():List.of(id);}
    private static Long optionalId(String id){return id==null?null:RequestValues.positiveId(id);}
    private static String text(String raw,int max,boolean required){return com.example.travelplatform.itinerary.validation.ItineraryText.normalize(raw,max,required);}
    private void touch(Itinerary root){changed(roots.touch(root.getId(),now()));}
    private static void owned(Itinerary root,long user){if(!Objects.equals(root.getUserId(),user))throw new ApiException(ErrorCode.FORBIDDEN);}
    private static <T extends Itinerary>T required(T root){if(root==null||!Integer.valueOf(0).equals(root.getIsDeleted()))throw missing();return root;}
    private static void changed(int count){if(count!=1)throw conflict("资源状态已变化，请重新加载");}
    private static ApiException invalid(){return new ApiException(ErrorCode.VALIDATION_FAILED,"行程字段格式或长度不正确");}
    private static ApiException missing(){return new ApiException(ErrorCode.NOT_FOUND);}
    private static ApiException conflict(String message){return new ApiException(ErrorCode.STATE_CONFLICT,message);}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);}
    private static OffsetDateTime offset(LocalDateTime time){return time.atOffset(ZoneOffset.ofHours(8));}
    private record RootValues(String title,Long destinationId,ItineraryDates.Range range,String notes,String status) { }
    private record OwnedDay(Itinerary root,ItineraryDay day) { }
    private record OwnedItem(Itinerary root,ItineraryDay day,ItineraryItem item) { }
}
