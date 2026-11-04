package com.example.travelplatform.administration.service;
import com.example.travelplatform.common.api.PageResponse;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.administration.dto.StatusPatchRequest;
import com.example.travelplatform.administration.mapper.AdministrationMapper;
import com.example.travelplatform.administration.vo.AdminUserVO;
import com.example.travelplatform.administration.vo.AdminSummaryVO;
import com.example.travelplatform.user.entity.User;
import com.example.travelplatform.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AdministrationService {
    private static final Set<String> STATUSES=Set.of("ACTIVE","DISABLED"),ROLES=Set.of("USER","ADMIN");
    private final AdministrationMapper admin;
    private final UserService session;
    public AdministrationService(AdministrationMapper admin,UserService session){this.admin=admin;this.session=session;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PageResponse<AdminUserVO> users(String rawPage,String rawSize,String keyword,String status,String role){
        session.requireAdministrator();long page=RequestValues.page(rawPage),size=RequestValues.size(rawSize),offset=RequestValues.offset(page,size);
        if((status!=null&&!STATUSES.contains(status))||(role!=null&&!ROLES.contains(role)))throw invalid();
        String word=RequestValues.keyword(keyword),pattern=word==null?null:RequestValues.likePattern(word);long total=admin.countUsers(pattern,status,role);
        List<User> rows=offset>=total?List.of():admin.users(pattern,status,role,size,offset);
        return new PageResponse<>(rows.stream().map(AdminUserVO::from).toList(),total,page,size,total/size+(total%size==0?0:1));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AdminSummaryVO summary(){session.requireAdministrator();return admin.summary();}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public AdminUserVO userStatus(String rawId,StatusPatchRequest request){
        long actor=session.requireAdministrator().getId(),id=RequestValues.positiveId(rawId);
        if(request==null||request.status()==null||!STATUSES.contains(request.status()))throw invalid();
        List<User> administrators=admin.lockAdministrators();
        User current=administrators.stream().filter(user->Objects.equals(user.getId(),actor)).findFirst().orElse(null);
        if(!available(current))throw new ApiException(ErrorCode.UNAUTHORIZED);
        User target=admin.lockUser(id);if(target==null||!Integer.valueOf(0).equals(target.getIsDeleted()))throw new ApiException(ErrorCode.NOT_FOUND);
        if("DISABLED".equals(request.status())){
            if(id==actor)throw new ApiException(ErrorCode.STATE_CONFLICT,"不能禁用当前管理员账号");
            if("ADMIN".equals(target.getRole())&&available(target)&&administrators.stream().filter(AdministrationService::available).count()<2)
                throw new ApiException(ErrorCode.STATE_CONFLICT,"至少需要保留一位可用管理员");
        }
        if(target.getStatus().equals(request.status()))return AdminUserVO.from(target);
        LocalDateTime time=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);
        if(admin.userStatus(id,request.status(),time)!=1)throw new ApiException(ErrorCode.STATE_CONFLICT);
        target.setStatus(request.status());target.setUpdatedAt(time);return AdminUserVO.from(target);
    }
    private static boolean available(User user){return user!=null&&"ADMIN".equals(user.getRole())&&"ACTIVE".equals(user.getStatus())&&Integer.valueOf(0).equals(user.getIsDeleted());}
    private static ApiException invalid(){return new ApiException(ErrorCode.VALIDATION_FAILED,"用户筛选或状态参数不正确");}
}
