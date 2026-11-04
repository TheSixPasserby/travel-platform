package com.example.travelplatform.interaction.service;
import com.example.travelplatform.common.error.*;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.guide.entity.Guide;
import com.example.travelplatform.guide.mapper.*;
import com.example.travelplatform.interaction.mapper.*;
import com.example.travelplatform.interaction.vo.GuideInteractionVO;
import com.example.travelplatform.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class InteractionService {
    private final GuideMapper guides;
    private final InteractionMapper interactions;
    private final UserService session;
    public InteractionService(GuideMapper guides,InteractionMapper interactions,UserService session){this.guides=guides;this.interactions=interactions;this.session=session;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public GuideInteractionVO state(String id){return state(RequestValues.positiveId(id),session.requireCurrentUser().getId());}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideInteractionVO like(String id,boolean enabled){
        long user=session.requireCurrentUser().getId(),guide=RequestValues.positiveId(id);visible(guides.lock(guide));
        if(enabled)interactions.like(user,guide);else interactions.unlike(user,guide);
        return state(guide,user);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public GuideInteractionVO favorite(String id,boolean enabled){
        long user=session.requireCurrentUser().getId(),guide=RequestValues.positiveId(id);visible(guides.lock(guide));
        if(enabled)interactions.favorite(user,guide);else interactions.unfavorite(user,guide);
        return state(guide,user);
    }
    private GuideInteractionVO state(long id,long user){
        GuideRow row=guides.detail(id);visible(row);InteractionFlags flags=interactions.flags(user,id);
        return new GuideInteractionVO(Long.toString(id),flags.liked(),flags.favorited(),row.getLikeCount(),row.getFavoriteCount(),row.getCommentCount());
    }
    private static void visible(Guide guide){
        if(guide==null||!Integer.valueOf(0).equals(guide.getIsDeleted())||!"PUBLISHED".equals(guide.getStatus()))throw new ApiException(ErrorCode.NOT_FOUND);
    }
}
