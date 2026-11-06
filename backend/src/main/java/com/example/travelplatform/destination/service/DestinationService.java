package com.example.travelplatform.destination.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.destination.dto.*;
import com.example.travelplatform.destination.entity.Destination;
import com.example.travelplatform.destination.mapper.DestinationMapper;
import com.example.travelplatform.destination.mapper.DestinationImageMapper;
import com.example.travelplatform.destination.validation.DestinationDetailsPolicy;
import com.example.travelplatform.destination.validation.DestinationDetailsValues;
import com.example.travelplatform.destination.validation.DestinationInputPolicy;
import com.example.travelplatform.destination.validation.DestinationValues;
import com.example.travelplatform.destination.vo.DestinationVO;
import com.example.travelplatform.destination.vo.DestinationImageVO;
import com.example.travelplatform.file.ManagedImagePaths;
import com.example.travelplatform.security.AdminAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DestinationService {
    private final DestinationMapper destinations;
    private final AdminAccess administrators;
    private final ManagedImagePaths images;
    private final DestinationImageMapper gallery;

    public DestinationService(DestinationMapper destinations, AdminAccess administrators, ManagedImagePaths images,DestinationImageMapper gallery) {
        this.destinations = destinations; this.administrators = administrators; this.images = images;this.gallery=gallery;
    }

    public PageResponse<DestinationVO> publicList(String page, String size, String keyword, String type, String parent) {
        return query(page, size, keyword, type, parent, "ACTIVE",true);
    }
    public PageResponse<DestinationVO> adminList(String page, String size, String keyword, String type, String parent, String status) {
        administrators.require();
        return query(page, size, keyword, type, parent, status == null ? null : DestinationInputPolicy.status(status),false);
    }
    private PageResponse<DestinationVO> query(String rawPage, String rawSize, String rawKeyword, String type, String parent, String status,boolean publicOnly) {
        long page = RequestValues.page(rawPage), size = RequestValues.size(rawSize);
        RequestValues.offset(page, size);
        String keyword = RequestValues.keyword(rawKeyword);
        var where = Wrappers.<Destination>lambdaQuery();
        if (status != null) where.eq(Destination::getStatus, status);
        if (type != null) where.eq(Destination::getDestinationType, DestinationInputPolicy.type(type));
        if (parent != null) where.eq(Destination::getParentId, RequestValues.positiveId(parent));
        if (keyword != null) where.apply("name LIKE {0} ESCAPE '!'", RequestValues.likePattern(keyword));
        if(publicOnly)where.apply("(destination_type <> {0} OR EXISTS (SELECT 1 FROM destinations parent_city WHERE parent_city.id=destinations.parent_id AND parent_city.destination_type={1} AND parent_city.status={2} AND parent_city.is_deleted=0))","SCENIC_SPOT","CITY","ACTIVE");
        where.orderByAsc(Destination::getSortOrder, Destination::getId);
        var result=destinations.selectPage(new Page<>(page,size),where);
        return new PageResponse<>(views(result.getRecords()),result.getTotal(),result.getCurrent(),result.getSize(),result.getPages());
    }

    public DestinationVO publicDetail(String rawId) {
        Destination destination = destinations.selectById(RequestValues.positiveId(rawId));
        if (!available(destination)) throw new ApiException(ErrorCode.NOT_FOUND);
        return view(destination);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO create(CreateDestinationRequest request) {
        administrators.require();
        DestinationValues values = DestinationInputPolicy.normalize(request);
        images.validate(values.coverImageUrl());
        Map<Long, Destination> graph = lockedGraph();
        checkParent(null, values.destinationType(),values.parentId(),null,graph);
        DestinationDetailsValues details=details(request,null);
        Destination destination = new Destination();
        fill(destination, values);
        fillDetails(destination,details);
        destination.setIsDeleted(0);
        destination.setCreatedAt(now()); destination.setUpdatedAt(destination.getCreatedAt());
        destinations.insert(destination);
        replaceGallery(destination.getId(),details);
        return view(destinations.selectById(destination.getId()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO update(String rawId, DestinationWriteRequest request) {
        administrators.require();
        long id = RequestValues.positiveId(rawId);
        DestinationValues values = DestinationInputPolicy.normalize(request);
        images.validate(values.coverImageUrl());
        Map<Long, Destination> graph = lockedGraph();
        Destination previous = requireDestination(id, graph);
        if("SCENIC_SPOT".equals(previous.getDestinationType())&&(!"SCENIC_SPOT".equals(values.destinationType())||!Objects.equals(previous.getParentId(),values.parentId()))&&destinations.guideLinks(id)>0)
            throw new ApiException(ErrorCode.STATE_CONFLICT,"景点已有关联攻略，不能直接变更类型或所属城市");
        checkParent(id,values.destinationType(),values.parentId(),previous.getParentId(),graph);
        DestinationDetailsValues details=details(request,previous);
        var update = Wrappers.<Destination>lambdaUpdate().eq(Destination::getId, id)
                .set(Destination::getName, values.name()).set(Destination::getDestinationType, values.destinationType())
                .set(Destination::getParentId, values.parentId()).set(Destination::getCountry, values.country())
                .set(Destination::getProvince, values.province()).set(Destination::getCity, values.city())
                .set(Destination::getDescription, values.description()).set(Destination::getCoverImageUrl, values.coverImageUrl())
                .set(Destination::getRecommendedSeasons, values.recommendedSeasons()).set(Destination::getStatus, values.status())
                .set(Destination::getSortOrder, values.sortOrder()).set(Destination::getUpdatedAt, now())
                .set(Destination::getAddress,details.address()).set(Destination::getLongitude,details.longitude()).set(Destination::getLatitude,details.latitude())
                .set(Destination::getRecommendedDurationMinutes,details.recommendedDurationMinutes()).set(Destination::getRecommendedDays,details.recommendedDays())
                .set(Destination::getOpeningHours,details.openingHours()).set(Destination::getTicketInfo,details.ticketInfo())
                .set(Destination::getOfficialUrl,details.officialUrl()).set(Destination::getSourceUrl,details.sourceUrl()).set(Destination::getVerifiedAt,details.verifiedAt());
        if (destinations.update(null, update) != 1) throw new ApiException(ErrorCode.NOT_FOUND);
        replaceGallery(id,details);
        return view(destinations.selectById(id));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO updateStatus(String rawId, DestinationStatusRequest request) {
        administrators.require();
        long id = RequestValues.positiveId(rawId);
        String status = DestinationInputPolicy.status(request.status());
        Destination current = requireDestination(id, lockedGraph());
        if (status.equals(current.getStatus())) return view(current);
        destinations.update(null, Wrappers.<Destination>lambdaUpdate().eq(Destination::getId, id)
                .set(Destination::getStatus, status).set(Destination::getUpdatedAt, now()));
        return view(destinations.selectById(id));
    }

    private Map<Long, Destination> lockedGraph() {
        Map<Long, Destination> graph = new HashMap<>();
        for (Destination destination : destinations.lockGraph()) graph.put(destination.getId(), destination);
        return graph;
    }
    private static Destination requireDestination(long id, Map<Long, Destination> graph) {
        Destination value = graph.get(id);
        if (!available(value)) throw new ApiException(ErrorCode.NOT_FOUND);
        return value;
    }
    private static void checkParent(Long self,String type,Long parent,Long oldParent,Map<Long,Destination> graph) {
        if(("CITY".equals(type)&&parent!=null)||("SCENIC_SPOT".equals(type)&&parent==null))
            throw new ApiException(ErrorCode.VALIDATION_FAILED,"城市不能有父级，景点必须归属城市");
        if(self!=null&&!"CITY".equals(type)&&graph.values().stream().anyMatch(d->Integer.valueOf(0).equals(d.getIsDeleted())&&"SCENIC_SPOT".equals(d.getDestinationType())&&Objects.equals(d.getParentId(),self)))
            throw new ApiException(ErrorCode.STATE_CONFLICT,"该城市仍有关联景点，不能变更为其他类型");
        if (parent == null) return;
        Destination target = graph.get(parent);
        if("SCENIC_SPOT".equals(type)&&(target==null||!"CITY".equals(target.getDestinationType())))
            throw new ApiException(ErrorCode.VALIDATION_FAILED,"景点的父级必须是城市");
        if (!available(target) || (!Objects.equals(parent, oldParent) && !"ACTIVE".equals(target.getStatus()))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "请选择可用的上级目的地");
        }
        Set<Long> seen = new HashSet<>();
        Long cursor = parent;
        while (cursor != null) {
            if (Objects.equals(cursor, self) || !seen.add(cursor)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "上级目的地不能形成循环");
            }
            Destination node = graph.get(cursor);
            if (node == null) throw new ApiException(ErrorCode.VALIDATION_FAILED, "上级目的地无效");
            cursor = node.getParentId();
        }
    }
    private static boolean available(Destination value) {
        return value != null && Integer.valueOf(0).equals(value.getIsDeleted());
    }
    private DestinationDetailsValues details(DestinationWriteRequest request,Destination previous){
        DestinationDetailsValues value=DestinationDetailsPolicy.normalize(DestinationDetailsMerge.merge(request,previous));
        if(value.images()!=null)for(var image:value.images())images.validate(image.url());
        return value;
    }
    private void replaceGallery(long id,DestinationDetailsValues value){
        if(value.images()==null)return;
        gallery.deleteForDestination(id);
        for(int n=0;n<value.images().size();n++)gallery.add(id,value.images().get(n),n);
    }
    private DestinationVO view(Destination d){return views(List.of(d)).getFirst();}
    private List<DestinationVO> views(List<Destination> rows){
        if(rows.isEmpty())return List.of();
        Map<Long,List<DestinationImageVO>> groups=new HashMap<>();
        for(var row:gallery.forDestinations(rows.stream().map(Destination::getId).toList()))
            groups.computeIfAbsent(row.destinationId(),ignored->new ArrayList<>()).add(DestinationImageVO.from(row));
        return rows.stream().map(d->DestinationVO.from(d,groups.getOrDefault(d.getId(),List.of()))).toList();
    }
    private static void fillDetails(Destination d,DestinationDetailsValues v){
        d.setAddress(v.address());d.setLongitude(v.longitude());d.setLatitude(v.latitude());d.setRecommendedDurationMinutes(v.recommendedDurationMinutes());
        d.setRecommendedDays(v.recommendedDays());d.setOpeningHours(v.openingHours());d.setTicketInfo(v.ticketInfo());
        d.setOfficialUrl(v.officialUrl());d.setSourceUrl(v.sourceUrl());d.setVerifiedAt(v.verifiedAt());
    }
    private static void fill(Destination target, DestinationValues value) {
        target.setName(value.name()); target.setDestinationType(value.destinationType()); target.setParentId(value.parentId());
        target.setCountry(value.country()); target.setProvince(value.province()); target.setCity(value.city());
        target.setDescription(value.description()); target.setCoverImageUrl(value.coverImageUrl());
        target.setRecommendedSeasons(value.recommendedSeasons()); target.setStatus(value.status()); target.setSortOrder(value.sortOrder());
    }
    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);
    }
}
