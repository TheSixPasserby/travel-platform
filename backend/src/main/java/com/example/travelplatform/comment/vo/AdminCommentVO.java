package com.example.travelplatform.comment.vo;
import com.example.travelplatform.comment.entity.GuideComment;
import com.example.travelplatform.user.vo.PublicProfileVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
public record AdminCommentVO(String id,String guideId,String guideTitle,PublicProfileVO author,String content,String status,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt) {
    public static AdminCommentVO from(GuideComment row,String title,PublicProfileVO author){return new AdminCommentVO(row.getId().toString(),row.getGuideId().toString(),title,author,row.getContent(),row.getStatus(),row.getCreatedAt().atOffset(ZoneOffset.ofHours(8)),row.getUpdatedAt().atOffset(ZoneOffset.ofHours(8)));}
}
