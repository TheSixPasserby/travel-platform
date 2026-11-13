package com.example.travelplatform.comment.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.comment.dto.CommentCreateRequest;
import com.example.travelplatform.comment.entity.GuideComment;
import com.example.travelplatform.comment.mapper.CommentMapper;
import com.example.travelplatform.comment.mapper.AdminCommentRow;
import com.example.travelplatform.comment.validation.CommentInputPolicy;
import com.example.travelplatform.comment.vo.CommentVO;
import com.example.travelplatform.comment.vo.AdminCommentVO;
import com.example.travelplatform.guide.entity.Guide;
import com.example.travelplatform.guide.mapper.GuideMapper;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.user.mapper.UserMapper;
import com.example.travelplatform.user.service.UserService;
import com.example.travelplatform.user.vo.PublicProfileVO;
import org.springframework.stereotype.Service;
import com.example.travelplatform.common.ip.RequestIpRegion;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommentService {
    private final RequestIpRegion ipRegions;
    private final CommentMapper comments;
    private final GuideMapper guides;
    private final UserMapper users;
    private final UserService session;
    private final com.example.travelplatform.notification.NotificationMapper notifications;
    public CommentService(CommentMapper comments,GuideMapper guides,UserMapper users,UserService session,RequestIpRegion ipRegions,com.example.travelplatform.notification.NotificationMapper notifications){this.notifications=notifications;this.ipRegions=ipRegions;this.comments=comments;this.guides=guides;this.users=users;this.session=session;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<CommentVO> list(String id,String rawPage,String rawSize){
        long guide=RequestValues.positiveId(id),page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        visible(guides.selectById(guide));long total=comments.countPublic(guide);
        List<GuideComment> rows=offset>=total?List.of():comments.page(guide,size,offset);
        Map<Long,User> authors=rows.isEmpty()?Map.of():users.selectList(Wrappers.<User>lambdaQuery().in(User::getId,rows.stream().map(GuideComment::getUserId).distinct().toList()))
                .stream().collect(Collectors.toMap(User::getId,Function.identity()));
        List<CommentVO> result=rows.stream().map(comment->{
            User user=authors.get(comment.getUserId());PublicProfileVO author=user==null?new PublicProfileVO(comment.getUserId().toString(),"已注销的旅行者",null,null,null):PublicProfileVO.from(user);
            return view(comment,author);
        }).toList();
        return new PageResponse<>(result,total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public CommentVO create(String id,CommentCreateRequest request){
        User user=session.requireCurrentUser();long guide=RequestValues.positiveId(id);
        String content=CommentInputPolicy.normalize(request==null?null:request.content());
        User locked=comments.lockAuthor(user.getId());
        if(locked==null||!"ACTIVE".equals(locked.getStatus())||locked.getIsDeleted()!=0)throw new ApiException(ErrorCode.UNAUTHORIZED);
        comments.serializeWrites(user.getId());
        visible(guides.lock(guide));
        LocalDateTime time=now();
        if(comments.recent(user.getId(),time.minusMinutes(1))>=10||comments.recent(user.getId(),time.minusDays(1))>=100)
            throw new ApiException(ErrorCode.COMMENT_RATE_LIMIT);
        GuideComment target=null;
        Long root=null;
        if(request.replyToId()!=null) {
            target=publicComment(RequestValues.positiveId(request.replyToId()));
            if(target.getGuideId()!=guide)throw new ApiException(ErrorCode.VALIDATION_FAILED,"回复必须属于当前攻略");
            root=target.getRootId()==null?target.getId():target.getRootId();
        }
        List<User> mentioned=validateMentions(content,request.mentionUserIds());
        GuideComment comment=new GuideComment();comment.setGuideId(guide);comment.setUserId(user.getId());comment.setContent(content);
        comment.setRootId(root);comment.setReplyToId(target==null?null:target.getId());
        comment.setIpRegion(ipRegions.current());comment.setStatus("NORMAL");comment.setIsDeleted(0);comment.setCreatedAt(time);comment.setUpdatedAt(time);comments.insert(comment);
        for(User recipient:mentioned)comments.mention(comment.getId(),recipient.getId());
        Map<Long,String> recipients=new LinkedHashMap<>();
        if(target==null)recipients.put(guides.selectById(guide).getAuthorId(),"COMMENT");
        else recipients.put(target.getUserId(),"REPLY");
        for(User recipient:mentioned)recipients.put(recipient.getId(),"MENTION");
        recipients.remove(user.getId());
        for(var recipient:recipients.entrySet()) {
            User receiver=users.selectById(recipient.getKey());
            if(receiver!=null&&"ACTIVE".equals(receiver.getStatus()))
                notifications.insert(recipient.getKey(),user.getId(),guide,comment.getId(),recipient.getValue(),time);
        }
        return view(comment,PublicProfileVO.from(user));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<CommentVO> replies(String rawId,String rawPage,String rawSize) {
        GuideComment root=publicComment(RequestValues.positiveId(rawId));
        if(root.getRootId()!=null)throw new ApiException(ErrorCode.VALIDATION_FAILED,"请选择根评论");
        long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        long total=comments.countReplies(root.getId());
        var rows=offset>=total?List.<GuideComment>of():comments.replies(root.getId(),size,offset);
        var values=rows.stream().map(c->view(c,profile(c.getUserId(),users.selectById(c.getUserId())))).toList();
        return new PageResponse<>(values,total,page,size,total/size+(total%size==0?0:1));
    }
    public record Location(String guideId,String rootId,long rootPage,long replyPage) { }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Location location(String rawId) {
        GuideComment c=publicComment(RequestValues.positiveId(rawId));
        long root=c.getRootId()==null?c.getId():c.getRootId();
        return new Location(c.getGuideId().toString(),Long.toString(root),comments.newerRoots(root)/10+1,c.getRootId()==null?1:comments.earlierReplies(c.getId())/10+1);
    }
    private GuideComment publicComment(long id) {
        GuideComment c=required(comments.find(id));
        if(!"NORMAL".equals(c.getStatus()))throw new ApiException(ErrorCode.NOT_FOUND);
        visible(guides.selectById(c.getGuideId()));
        if(c.getRootId()!=null) {
            GuideComment root=required(comments.find(c.getRootId()));
            if(!"NORMAL".equals(root.getStatus()))throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return c;
    }
    private List<User> validateMentions(String content,List<String> rawIds) {
        if(rawIds==null||rawIds.isEmpty())return List.of();
        if(rawIds.size()>5)throw new ApiException(ErrorCode.VALIDATION_FAILED,"每条评论最多提及五人");
        Set<Long> ids=new LinkedHashSet<>();
        for(String raw:rawIds)if(!ids.add(RequestValues.positiveId(raw)))throw new ApiException(ErrorCode.VALIDATION_FAILED,"提及用户不能重复");
        List<User> result=new ArrayList<>();
        for(long id:ids) {
            User u=users.selectById(id);
            if(u==null||!"ACTIVE".equals(u.getStatus())||!java.util.regex.Pattern.compile("(?<![A-Za-z0-9_@])@"+java.util.regex.Pattern.quote(u.getUsername())+"(?![A-Za-z0-9_])",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content).find())
                throw new ApiException(ErrorCode.VALIDATION_FAILED,"提及账号无效或正文缺少对应的 @用户名");
            result.add(u);
        }
        return result;
    }
    private CommentVO view(GuideComment c,PublicProfileVO author) {
        GuideComment target=c.getReplyToId()==null?null:comments.find(c.getReplyToId());
        PublicProfileVO replyTo=target==null?null:profile(target.getUserId(),users.selectById(target.getUserId()));
        return CommentVO.from(c,author,replyTo,c.getRootId()==null?comments.countReplies(c.getId()):0,comments.mentions(c.getId()).stream().map(PublicProfileVO::from).toList());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void delete(String id){
        long user=session.requireCurrentUser().getId(),commentId=RequestValues.positiveId(id);
        GuideComment located=required(comments.find(commentId));
        // Locate without a comment lock, then always lock guide before comment like all other writers.
        Guide guide=guides.lock(located.getGuideId());if(guide==null)throw new ApiException(ErrorCode.NOT_FOUND);
        GuideComment current=required(comments.lock(commentId));
        if(!Objects.equals(current.getGuideId(),guide.getId()))throw new ApiException(ErrorCode.STATE_CONFLICT);
        if(!Objects.equals(current.getUserId(),user))throw new ApiException(ErrorCode.FORBIDDEN);
        // Author management is allowed even for hidden comments/nonpublic or logically deleted parents.
        if(comments.update(null,Wrappers.<GuideComment>lambdaUpdate().eq(GuideComment::getId,commentId)
                .set(GuideComment::getIsDeleted,1).set(GuideComment::getUpdatedAt,now()))!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<AdminCommentVO> adminList(String rawPage,String rawSize,String keyword,String status){
        session.requireAdministrator();if(status!=null&&!Set.of("NORMAL","HIDDEN").contains(status))throw new ApiException(ErrorCode.VALIDATION_FAILED,"评论筛选状态不正确");
        long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        String word=RequestValues.keyword(keyword),pattern=word==null?null:RequestValues.likePattern(word);long total=comments.countAdmin(pattern,status);
        List<AdminCommentRow> rows=offset>=total?List.of():comments.adminPage(pattern,status,size,offset);
        Map<Long,User> authors=rows.isEmpty()?Map.of():users.selectList(Wrappers.<User>lambdaQuery().in(User::getId,rows.stream().map(GuideComment::getUserId).distinct().toList()))
                .stream().collect(Collectors.toMap(User::getId,Function.identity()));
        List<AdminCommentVO> result=rows.stream().map(row->AdminCommentVO.from(row,row.getGuideTitle(),profile(row.getUserId(),authors.get(row.getUserId())))).toList();
        return new PageResponse<>(result,total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AdminCommentVO adminStatus(String rawId,StatusPatchRequest request){
        session.requireAdministrator();if(request==null||request.status()==null||!Set.of("NORMAL","HIDDEN").contains(request.status()))throw new ApiException(ErrorCode.VALIDATION_FAILED,"评论治理状态不正确");
        long id=RequestValues.positiveId(rawId);GuideComment initial=required(comments.find(id));Guide parent=guides.lock(initial.getGuideId());
        if(parent==null)throw new ApiException(ErrorCode.NOT_FOUND);GuideComment current=required(comments.lock(id));
        if(!Objects.equals(current.getGuideId(),parent.getId()))throw new ApiException(ErrorCode.STATE_CONFLICT);
        if(!current.getStatus().equals(request.status())){
            LocalDateTime time=now();if(comments.update(null,Wrappers.<GuideComment>lambdaUpdate().eq(GuideComment::getId,id).set(GuideComment::getStatus,request.status()).set(GuideComment::getUpdatedAt,time))!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
            current.setStatus(request.status());current.setUpdatedAt(time);
        }
        return AdminCommentVO.from(current,parent.getTitle(),profile(current.getUserId(),users.selectById(current.getUserId())));
    }
    private static PublicProfileVO profile(Long id,User user){return user==null?new PublicProfileVO(id.toString(),"已注销的旅行者",null,null,null):PublicProfileVO.from(user);}
    private static GuideComment required(GuideComment comment){
        if(comment==null||!Integer.valueOf(0).equals(comment.getIsDeleted()))throw new ApiException(ErrorCode.NOT_FOUND);return comment;
    }
    private void visible(Guide guide){
        if(guide==null||!Integer.valueOf(0).equals(guide.getIsDeleted())||!"PUBLISHED".equals(guide.getStatus()))throw new ApiException(ErrorCode.NOT_FOUND);
    }
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);}
}
