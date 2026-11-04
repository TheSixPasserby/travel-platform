package com.example.travelplatform.itinerary.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;
public record ItineraryDetailVO(String id,String title,ItineraryDestination destination,LocalDate startDate,LocalDate endDate,
        String status,long dayCount,long itemCount,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt,String notes,List<DayVO> days) {
    public ItineraryDetailVO {days=List.copyOf(days);}
    public static ItineraryDetailVO from(ItinerarySummaryVO s,String notes,List<DayVO> days){
        return new ItineraryDetailVO(s.id(),s.title(),s.destination(),s.startDate(),s.endDate(),s.status(),s.dayCount(),s.itemCount(),s.createdAt(),s.updatedAt(),notes,days);
    }
}
