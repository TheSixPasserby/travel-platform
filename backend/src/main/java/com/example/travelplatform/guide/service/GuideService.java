package com.example.travelplatform.guide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.destination.mapper.DestinationMapper;
import com.example.travelplatform.file.ManagedImagePaths;
import com.example.travelplatform.guide.dto.*;
import com.example.travelplatform.guide.entity.Guide;
import com.example.travelplatform.guide.mapper.*;
import com.example.travelplatform.guide.validation.*;
import com.example.travelplatform.guide.vo.*;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.user.mapper.UserMapper;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.PublicProfileVO;
import org.springframework.stereotype.Service;
import com.example.travelplatform.common.ip.RequestIpRegion;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GuideService {
    private static final Set<String> STATUSES=Set.of("DRAFT","PUBLISHED","HIDDEN");
    private final RequestIpRegion ipRegions;
    private final GuideMapper guides;
    private final UserService session;
    private final UserMapper users;
    private final DestinationMapper destinations;
    private final ManagedImagePaths images;
    private final GuideDestinationsMapper spots;
    public GuideService(GuideMapper guides,UserService session,UserMapper users,DestinationMapper destinations,ManagedImagePaths images,GuideDestinationsMapper spots,RequestIpRegion ipRegions) {
        this.ipRegions=ipRegions;
        this.guides=guides;this.session=session;this.users=users;this.destinations=destinations;this.images=images;this.spots=spots;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<GuideSummaryVO> publicList(GuideQuery q) {
        String sort=q.sort()==null?"latest":q.sort();
        if (!Set.of("latest","popular").contains(sort)) throw invalid();
        return page(new GuideSearch(null,"PUBLISHED",pattern(q.keyword()),id(q.destinationId()),id(q.tagId()),sort.equals("popular")),q.page(),q.size());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<GuideSummaryVO> mine(MyGuideQuery q) {
        long author=session.requireCurrentUser().getId();
        if(q.status()!=null&&!STATUSES.contains(q.status()))throw invalid();
        return page(new GuideSearch(author,q.status(),pattern(q.keyword()),null,null,false),q.page(),q.size());
    }
    private PageResponse<GuideSummaryVO> page(GuideSearch filter,String rawPage,String rawSize) {
        long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        long total=guides.count(filter);
        List<GuideRow> rows=offset>=total?List.of():guides.page(filter,size,offset);
        return new PageResponse<>(summaries(rows),total,page,size,total/size+(total%size==0?0:1));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<GuideSummaryVO> favorites(String rawPage,String rawSize,String keyword) {
        long user=session.requireCurrentUser().getId(),page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        String pattern=pattern(keyword);long total=guides.countFavorites(user,pattern);
        List<GuideRow> rows=offset>=total?List.of():guides.favorites(user,pattern,size,offset);
        return new PageResponse<>(summaries(rows),total,page,size,total/size+(total%size==0?0:1));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GuideDetailVO publicDetail(String id) {
        GuideRow row=required(guides.detail(RequestValues.positiveId(id)));
        if(!"PUBLISHED".equals(row.getStatus()))throw new ApiException(ErrorCode.NOT_FOUND);
        return detail(row);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GuideDetailVO ownDetail(String id) {
        long author=session.requireCurrentUser().getId();
        GuideRow row=required(guides.detail(RequestValues.positiveId(id)));
        if(!Objects.equals(row.getAuthorId(),author))throw new ApiException(ErrorCode.FORBIDDEN);
        return detail(row);
    }
    public List<TagVO> tags() { return List.copyOf(guides.allTags()); }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<GuideSummaryVO> relatedDestination(String rawId,String rawPage,String rawSize,String keyword,String tag){
        long destinationId=RequestValues.positiveId(rawId),page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        Destination d=destinations.selectById(destinationId);if(d==null)throw new ApiException(ErrorCode.NOT_FOUND);
        String search=pattern(keyword);Long tagId=id(tag);long total=guides.countRelated(destinationId,d.getDestinationType(),search,tagId);
        List<GuideRow> rows=offset>=total?List.of():guides.pageRelated(destinationId,d.getDestinationType(),search,tagId,size,offset);
        return new PageResponse<>(summaries(rows),total,page,size,total/size+(total%size==0?0:1));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<GuideSummaryVO> adminList(String rawPage,String rawSize,String keyword,String status) {
        session.requireAdministrator();if(status!=null&&!STATUSES.contains(status))throw invalid();
        long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        GuideSearch filter=new GuideSearch(null,status,pattern(keyword),null,null,false);long total=guides.count(filter);
        List<GuideRow> rows=offset>=total?List.of():guides.adminPage(filter,size,offset);
        return new PageResponse<>(summaries(rows),total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideSummaryVO adminStatus(String rawId,StatusPatchRequest request) {
        session.requireAdministrator();
        if(request==null||request.status()==null||!Set.of("PUBLISHED","HIDDEN").contains(request.status()))throw invalid();
        long id=RequestValues.positiveId(rawId);Guide current=guides.lock(id);
        if(current==null||!Integer.valueOf(0).equals(current.getIsDeleted()))throw new ApiException(ErrorCode.NOT_FOUND);
        if("DRAFT".equals(current.getStatus())||("PUBLISHED".equals(request.status())&&current.getPublishedAt()==null))
            throw new ApiException(ErrorCode.STATE_CONFLICT,"只能隐藏或恢复曾经发布的攻略，不能代替作者发布草稿");
        if(!current.getStatus().equals(request.status())){
            if(guides.update(null,Wrappers.<Guide>lambdaUpdate().eq(Guide::getId,id).set(Guide::getStatus,request.status()).set(Guide::getUpdatedAt,now()))!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        return summaries(List.of(required(guides.detail(id)))).getFirst();
    }

    // READ_COMMITTED avoids reusing the authentication SELECT's old snapshot after waiting for a row lock.
    // The locked guide serializes every author write; related rows are then read from the latest committed state.
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideDetailVO create(CreateGuideRequest request) {
        User author=session.requireCurrentUser();
        GuideValues value=GuideInputPolicy.normalize(request);String status=GuideInputPolicy.creationStatus(request);
        if(status.equals("PUBLISHED"))GuideInputPolicy.requirePublishable(value);
        references(value,null);
        List<Long> scenicIds=resolveSpots(request,value.destinationId(),null,null);
        Guide guide=new Guide();guide.setAuthorId(author.getId());guide.setStatus(status);guide.setIsDeleted(0);
        guide.setCreatedAt(now());guide.setUpdatedAt(guide.getCreatedAt());
        if(status.equals("PUBLISHED")){guide.setPublishedAt(guide.getCreatedAt());guide.setIpRegion(ipRegions.current());}
        apply(guide,value);guides.insert(guide);replaceRelations(guide.getId(),value);replaceSpots(guide.getId(),scenicIds);
        return detail(required(guides.detail(guide.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideDetailVO update(String id,UpdateGuideRequest request) {
        Guide guide=lockOwned(id);editable(guide);
        GuideValues value=GuideInputPolicy.normalize(request);
        if("PUBLISHED".equals(guide.getStatus()))GuideInputPolicy.requirePublishable(value);
        references(value,guide.getDestinationId());
        List<Long> scenicIds=resolveSpots(request,value.destinationId(),guide.getId(),guide.getDestinationId());
        LocalDateTime time=now();
        var update=Wrappers.<Guide>lambdaUpdate().eq(Guide::getId,guide.getId())
                .set(Guide::getTitle,value.title()).set(Guide::getContent,value.content()).set(Guide::getSummary,value.summary())
                .set(Guide::getDestinationId,value.destinationId()).set(Guide::getCoverImageUrl,value.coverImageUrl())
                .set(Guide::getTravelDays,value.travelDays()).set(Guide::getBudgetPerPerson,value.budgetPerPerson())
                .set(Guide::getSeason,value.season()).set(Guide::getUpdatedAt,time);
        if(guides.update(null,update)!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
        replaceRelations(guide.getId(),value);
        replaceSpots(guide.getId(),scenicIds);
        return detail(required(guides.detail(guide.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideDetailVO publish(String id) {
        Guide guide=lockOwned(id);editable(guide);
        if("PUBLISHED".equals(guide.getStatus()))return detail(required(guides.detail(guide.getId())));
        GuideValues value=stored(guide);GuideInputPolicy.requirePublishable(value);references(value,guide.getDestinationId());
        var update=Wrappers.<Guide>lambdaUpdate().eq(Guide::getId,guide.getId()).set(Guide::getStatus,"PUBLISHED").set(Guide::getUpdatedAt,now());
        if(guide.getPublishedAt()==null)update.set(Guide::getPublishedAt,now()).set(Guide::getIpRegion,ipRegions.current());
        if(guides.update(null,update)!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
        return detail(required(guides.detail(guide.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideDetailVO unpublish(String id) {
        Guide guide=lockOwned(id);editable(guide);
        if(!"DRAFT".equals(guide.getStatus())) {
            if(guides.update(null,Wrappers.<Guide>lambdaUpdate().eq(Guide::getId,guide.getId())
                    .set(Guide::getStatus,"DRAFT").set(Guide::getUpdatedAt,now()))!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
        }
        return detail(required(guides.detail(guide.getId())));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(String id) {
        Guide guide=lockOwned(id);
        if(guides.update(null,Wrappers.<Guide>lambdaUpdate().eq(Guide::getId,guide.getId())
                .set(Guide::getIsDeleted,1).set(Guide::getUpdatedAt,now()))!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
    }

    private Guide lockOwned(String id) {
        long author=session.requireCurrentUser().getId();Guide guide=guides.lock(RequestValues.positiveId(id));
        if(guide==null||!Integer.valueOf(0).equals(guide.getIsDeleted()))throw new ApiException(ErrorCode.NOT_FOUND);
        if(!Objects.equals(guide.getAuthorId(),author))throw new ApiException(ErrorCode.FORBIDDEN);
        return guide;
    }
    private void references(GuideValues value,Long originalDestination) {
        if(value.destinationId()!=null) {
            Destination destination=guides.lockDestination(value.destinationId());
            if(destination==null||!Integer.valueOf(0).equals(destination.getIsDeleted())
                    ||(!Objects.equals(originalDestination,value.destinationId())&&!"ACTIVE".equals(destination.getStatus())))throw invalid();
        }
        images.validate(value.coverImageUrl());
        for(GuideImageRequest image:value.images())images.validate(image.url());
        if(!value.tagIds().isEmpty()&&guides.lockTags(value.tagIds()).size()!=value.tagIds().size())throw invalid();
    }
    private GuideValues stored(Guide guide) {
        var request=new UpdateGuideRequest();request.setTitle(guide.getTitle());request.setContent(guide.getContent());request.setSummary(guide.getSummary());
        request.setDestinationId(guide.getDestinationId()==null?null:guide.getDestinationId().toString());request.setCoverImageUrl(guide.getCoverImageUrl());
        request.setTravelDays(guide.getTravelDays());request.setBudgetPerPerson(guide.getBudgetPerPerson()==null?null:guide.getBudgetPerPerson().toPlainString());request.setSeason(guide.getSeason());
        request.setTagIds(guides.tagsFor(List.of(guide.getId())).stream().map(t->t.id().toString()).toList());
        request.setImages(guides.images(guide.getId()).stream().map(i->new GuideImageRequest(i.url(),i.altText())).toList());
        return GuideInputPolicy.normalize(request);
    }
    private void replaceRelations(long id,GuideValues value) {
        guides.deleteImages(id);guides.deleteTags(id);
        for(int i=0;i<value.images().size();i++){GuideImageRequest image=value.images().get(i);guides.addImage(id,image.url(),image.altText(),i);}
        for(long tag:value.tagIds())guides.addTag(id,tag);
    }
    private static void apply(Guide g,GuideValues v) {
        g.setTitle(v.title());g.setContent(v.content());g.setSummary(v.summary());g.setDestinationId(v.destinationId());g.setCoverImageUrl(v.coverImageUrl());
        g.setTravelDays(v.travelDays());g.setBudgetPerPerson(v.budgetPerPerson());g.setSeason(v.season());
    }
    private GuideDetailVO detail(GuideRow row) { return GuideDetailVO.from(summaries(List.of(row)).getFirst(),row.getContent(),guides.images(row.getId()),spots.references(row.getId())); }
    private List<Long> resolveSpots(GuideWriteRequest request,Long city,Long guide,Long originalCity){
        List<Long> old=guide==null?List.of():spots.ids(guide);
        if(!request.scenicSpotsProvided()){
            if(!old.isEmpty()&&!Objects.equals(city,originalCity))throw invalid();
            return guide==null?List.of():null;
        }
        if(request.getScenicSpotIds()==null||request.getScenicSpotIds().size()>20)throw invalid();
        List<Long> ids=request.getScenicSpotIds().stream().map(RequestValues::positiveId).sorted().toList();
        if(new HashSet<>(ids).size()!=ids.size())throw invalid();
        if(ids.isEmpty())return ids;
        if(city==null)throw invalid();
        Destination parent=guides.lockDestination(city);
        if(parent==null||!Integer.valueOf(0).equals(parent.getIsDeleted())||!"CITY".equals(parent.getDestinationType()))throw invalid();
        for(long id:ids){
            Destination spot=guides.lockDestination(id);boolean retained=Objects.equals(city,originalCity)&&old.contains(id);
            if(spot==null||!Integer.valueOf(0).equals(spot.getIsDeleted())||!"SCENIC_SPOT".equals(spot.getDestinationType())||!Objects.equals(spot.getParentId(),city)
                    ||(!retained&&(!"ACTIVE".equals(spot.getStatus())||!"ACTIVE".equals(parent.getStatus()))))throw invalid();
        }
        return ids;
    }
    private void replaceSpots(long id,List<Long> ids){if(ids==null)return;spots.clear(id);for(long destination:ids)spots.add(id,destination);}
    private List<GuideSummaryVO> summaries(List<GuideRow> rows) {
        if(rows.isEmpty())return List.of();
        List<Long> ids=rows.stream().map(Guide::getId).toList();
        Map<Long,User> authors=users.selectList(Wrappers.<User>lambdaQuery().in(User::getId,rows.stream().map(Guide::getAuthorId).distinct().toList()))
                .stream().collect(Collectors.toMap(User::getId,Function.identity()));
        List<Long> destinationIds=rows.stream().map(Guide::getDestinationId).filter(Objects::nonNull).distinct().toList();
        Map<Long,Destination> places=destinationIds.isEmpty()?Map.of():destinations.selectList(Wrappers.<Destination>lambdaQuery().in(Destination::getId,destinationIds))
                .stream().collect(Collectors.toMap(Destination::getId,Function.identity()));
        Map<Long,List<GuideTagRow>> tags=guides.tagsFor(ids).stream().collect(Collectors.groupingBy(GuideTagRow::guideId));
        return rows.stream().map(row->{
            User author=authors.get(row.getAuthorId());Destination destination=row.getDestinationId()==null?null:places.get(row.getDestinationId());
            PublicProfileVO profile=author==null?new PublicProfileVO(row.getAuthorId().toString(),"已注销的旅行者",null,null,null):PublicProfileVO.from(author);
            GuideSummaryVO.DestinationRef place=destination==null?null:new GuideSummaryVO.DestinationRef(destination.getId().toString(),destination.getName(),destination.getStatus());
            List<TagVO> labels=tags.getOrDefault(row.getId(),List.of()).stream().map(t->new TagVO(t.id().toString(),t.name())).toList();
            return new GuideSummaryVO(row.getId().toString(),row.getTitle(),row.getSummary(),row.getCoverImageUrl(),profile,place,row.getTravelDays(),
                    row.getBudgetPerPerson()==null?null:row.getBudgetPerPerson().setScale(2).toPlainString(),row.getSeason(),labels,row.getStatus(),
                    offset(row.getPublishedAt()),offset(row.getCreatedAt()),offset(row.getUpdatedAt()),row.getLikeCount(),row.getFavoriteCount(),row.getCommentCount(),row.getIpRegion());
        }).toList();
    }
    private static GuideRow required(GuideRow row) { if(row==null)throw new ApiException(ErrorCode.NOT_FOUND);return row; }
    private static void editable(Guide guide) { if("HIDDEN".equals(guide.getStatus()))throw new ApiException(ErrorCode.STATE_CONFLICT); }
    private static Long id(String id) { return id==null?null:RequestValues.positiveId(id); }
    private static String pattern(String raw) { String value=RequestValues.keyword(raw);return value==null?null:RequestValues.likePattern(value); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS); }
    private static OffsetDateTime offset(LocalDateTime time) { return time==null?null:time.atOffset(ZoneOffset.ofHours(8)); }
    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED,"攻略参数或关联内容无效"); }
}
