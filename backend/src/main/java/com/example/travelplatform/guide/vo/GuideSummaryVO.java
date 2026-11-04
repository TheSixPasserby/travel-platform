package com.example.travelplatform.guide.vo;
import com.example.travelplatform.user.vo.PublicProfileVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record GuideSummaryVO(String id, String title, String summary, String coverImageUrl,
        PublicProfileVO author, DestinationRef destination, Integer travelDays, String budgetPerPerson,
        String season, List<TagVO> tags, String status,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime publishedAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt,
        long likeCount, long favoriteCount, long commentCount) {
    public GuideSummaryVO { tags=List.copyOf(tags); }
    public record DestinationRef(String id, String name, String status) { }
}
