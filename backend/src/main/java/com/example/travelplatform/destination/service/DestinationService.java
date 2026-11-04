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
import com.example.travelplatform.destination.validation.DestinationInputPolicy;
import com.example.travelplatform.destination.validation.DestinationValues;
import com.example.travelplatform.destination.vo.DestinationVO;
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

    public DestinationService(DestinationMapper destinations, AdminAccess administrators, ManagedImagePaths images) {
        this.destinations = destinations; this.administrators = administrators; this.images = images;
    }

    public PageResponse<DestinationVO> publicList(String page, String size, String keyword, String type, String parent) {
        return query(page, size, keyword, type, parent, "ACTIVE");
    }
    public PageResponse<DestinationVO> adminList(String page, String size, String keyword, String type, String parent, String status) {
        administrators.require();
        return query(page, size, keyword, type, parent, status == null ? null : DestinationInputPolicy.status(status));
    }
    private PageResponse<DestinationVO> query(String rawPage, String rawSize, String rawKeyword, String type, String parent, String status) {
        long page = RequestValues.page(rawPage), size = RequestValues.size(rawSize);
        RequestValues.offset(page, size);
        String keyword = RequestValues.keyword(rawKeyword);
        var where = Wrappers.<Destination>lambdaQuery();
        if (status != null) where.eq(Destination::getStatus, status);
        if (type != null) where.eq(Destination::getDestinationType, DestinationInputPolicy.type(type));
        if (parent != null) where.eq(Destination::getParentId, RequestValues.positiveId(parent));
        if (keyword != null) where.apply("name LIKE {0} ESCAPE '!'", RequestValues.likePattern(keyword));
        where.orderByAsc(Destination::getSortOrder, Destination::getId);
        return PageResponse.from(destinations.selectPage(new Page<>(page, size), where), DestinationVO::from);
    }

    public DestinationVO publicDetail(String rawId) {
        Destination destination = destinations.selectById(RequestValues.positiveId(rawId));
        if (!available(destination)) throw new ApiException(ErrorCode.NOT_FOUND);
        return DestinationVO.from(destination);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO create(CreateDestinationRequest request) {
        administrators.require();
        DestinationValues values = DestinationInputPolicy.normalize(request);
        images.validate(values.coverImageUrl());
        Map<Long, Destination> graph = lockedGraph();
        checkParent(null, values.parentId(), null, graph);
        Destination destination = new Destination();
        fill(destination, values);
        destination.setIsDeleted(0);
        destination.setCreatedAt(now()); destination.setUpdatedAt(destination.getCreatedAt());
        destinations.insert(destination);
        return DestinationVO.from(destinations.selectById(destination.getId()));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO update(String rawId, DestinationWriteRequest request) {
        administrators.require();
        long id = RequestValues.positiveId(rawId);
        DestinationValues values = DestinationInputPolicy.normalize(request);
        images.validate(values.coverImageUrl());
        Map<Long, Destination> graph = lockedGraph();
        Destination previous = requireDestination(id, graph);
        checkParent(id, values.parentId(), previous.getParentId(), graph);
        var update = Wrappers.<Destination>lambdaUpdate().eq(Destination::getId, id)
                .set(Destination::getName, values.name()).set(Destination::getDestinationType, values.destinationType())
                .set(Destination::getParentId, values.parentId()).set(Destination::getCountry, values.country())
                .set(Destination::getProvince, values.province()).set(Destination::getCity, values.city())
                .set(Destination::getDescription, values.description()).set(Destination::getCoverImageUrl, values.coverImageUrl())
                .set(Destination::getRecommendedSeasons, values.recommendedSeasons()).set(Destination::getStatus, values.status())
                .set(Destination::getSortOrder, values.sortOrder()).set(Destination::getUpdatedAt, now());
        if (destinations.update(null, update) != 1) throw new ApiException(ErrorCode.NOT_FOUND);
        return DestinationVO.from(destinations.selectById(id));
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public DestinationVO updateStatus(String rawId, DestinationStatusRequest request) {
        administrators.require();
        long id = RequestValues.positiveId(rawId);
        String status = DestinationInputPolicy.status(request.status());
        Destination current = requireDestination(id, lockedGraph());
        if (status.equals(current.getStatus())) return DestinationVO.from(current);
        destinations.update(null, Wrappers.<Destination>lambdaUpdate().eq(Destination::getId, id)
                .set(Destination::getStatus, status).set(Destination::getUpdatedAt, now()));
        return DestinationVO.from(destinations.selectById(id));
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
    private static void checkParent(Long self, Long parent, Long oldParent, Map<Long, Destination> graph) {
        if (parent == null) return;
        Destination target = graph.get(parent);
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
