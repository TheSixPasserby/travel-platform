package com.example.travelplatform.comment.vo;
import com.example.travelplatform.comment.entity.GuideComment;
import com.example.travelplatform.user.vo.PublicProfileVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;

public record CommentVO(String id,String guideId,PublicProfileVO author,String content,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt,String ipRegion,
        String rootId,String replyToId,PublicProfileVO replyToAuthor,long replyCount,List<PublicProfileVO> mentions) {
    public CommentVO { ipRegion=com.example.travelplatform.common.ip.IpRegionVisibility.publicValue(ipRegion); }
    public static CommentVO from(GuideComment c,PublicProfileVO author) {
        return from(c,author,null,0,List.of());
    }
    public static CommentVO from(GuideComment c,PublicProfileVO author,PublicProfileVO replyTo,long replies,List<PublicProfileVO> mentions) {
        return new CommentVO(c.getId().toString(),c.getGuideId().toString(),author,c.getContent(),
            c.getCreatedAt().atOffset(ZoneOffset.ofHours(8)),c.getUpdatedAt().atOffset(ZoneOffset.ofHours(8)),c.getIpRegion(),
            c.getRootId()==null?null:c.getRootId().toString(),c.getReplyToId()==null?null:c.getReplyToId().toString(),replyTo,replies,mentions);
    }
}
