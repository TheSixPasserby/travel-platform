package com.example.travelplatform.comment.vo;
import com.example.travelplatform.comment.entity.GuideComment;
import com.example.travelplatform.user.vo.PublicProfileVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

public record CommentVO(String id,String guideId,PublicProfileVO author,String content,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt) {
    public static CommentVO from(GuideComment comment,PublicProfileVO author){
        return new CommentVO(comment.getId().toString(),comment.getGuideId().toString(),author,comment.getContent(),
                comment.getCreatedAt().atOffset(ZoneOffset.ofHours(8)),comment.getUpdatedAt().atOffset(ZoneOffset.ofHours(8)));
    }
}
