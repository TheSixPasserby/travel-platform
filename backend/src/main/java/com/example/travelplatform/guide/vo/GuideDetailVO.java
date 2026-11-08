package com.example.travelplatform.guide.vo;
import com.example.travelplatform.user.vo.PublicProfileVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
import java.util.List;

public record GuideDetailVO(String id, String title, String summary, String coverImageUrl,
        PublicProfileVO author, GuideSummaryVO.DestinationRef destination, Integer travelDays, String budgetPerPerson,
        String season, List<TagVO> tags, String status,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime publishedAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt,
        long likeCount, long favoriteCount, long commentCount, String ipRegion, String content, List<GuideImageVO> images,List<GuideSummaryVO.DestinationRef> scenicSpots) {
    public GuideDetailVO { tags=List.copyOf(tags);images=List.copyOf(images);scenicSpots=List.copyOf(scenicSpots); }
    public static GuideDetailVO from(GuideSummaryVO s, String content, List<GuideImageVO> images,List<GuideSummaryVO.DestinationRef> scenicSpots) {
        return new GuideDetailVO(s.id(),s.title(),s.summary(),s.coverImageUrl(),s.author(),s.destination(),s.travelDays(),
                s.budgetPerPerson(),s.season(),s.tags(),s.status(),s.publishedAt(),s.createdAt(),s.updatedAt(),
                s.likeCount(),s.favoriteCount(),s.commentCount(),s.ipRegion(),content,images,scenicSpots);
    }
}
