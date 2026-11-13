package com.example.travelplatform.notification;

import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.user.mapper.UserMapper;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.PublicProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationMapper mapper;
    private final UserService session;
    private final UserMapper users;
    public NotificationService(NotificationMapper mapper,UserService session,UserMapper users) {
        this.mapper=mapper;this.session=session;this.users=users;
    }
    public record NotificationVO(String id,String type,PublicProfileVO actor,String guideId,String commentId,String content,
                                  OffsetDateTime createdAt,OffsetDateTime readAt,String href) { }
    public record Unread(long count) { }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<NotificationVO> list(String rawPage,String rawSize,String unreadOnly) {
        long user=session.requireCurrentUser().getId();
        if(unreadOnly!=null&&!List.of("true","false").contains(unreadOnly))throw new ApiException(ErrorCode.VALIDATION_FAILED);
        boolean unread="true".equals(unreadOnly);
        long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        long total=mapper.count(user,unread);
        var rows=offset>=total?List.<NotificationRow>of():mapper.page(user,unread,size,offset);
        var values=rows.stream().map(n->new NotificationVO(Long.toString(n.id()),n.type(),PublicProfileVO.from(users.selectById(n.actorId())),
            Long.toString(n.guideId()),Long.toString(n.commentId()),n.content(),n.createdAt().atOffset(ZoneOffset.ofHours(8)),
            n.readAt()==null?null:n.readAt().atOffset(ZoneOffset.ofHours(8)),"/guides/"+n.guideId()+"?comments=1&commentId="+n.commentId())).toList();
        return new PageResponse<>(values,total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(readOnly=true)
    public Unread unread() { return new Unread(mapper.count(session.requireCurrentUser().getId(),true)); }
    @Transactional
    public void mark(String rawId) {
        long user=session.requireCurrentUser().getId(),id=RequestValues.positiveId(rawId);
        if(mapper.owned(id,user)==0)throw new ApiException(ErrorCode.NOT_FOUND);
        mapper.mark(id,user,LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }
    @Transactional
    public void markAll() { mapper.markAll(session.requireCurrentUser().getId(),LocalDateTime.now(ZoneId.of("Asia/Shanghai"))); }
    @Transactional(readOnly=true)
    public List<PublicProfileVO> suggestions(String rawKeyword) {
        session.requireCurrentUser();String keyword=RequestValues.keyword(rawKeyword);
        return keyword==null?List.of():mapper.suggestions(RequestValues.likePattern(keyword)).stream().map(PublicProfileVO::from).toList();
    }
}
