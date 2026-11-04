package com.example.travelplatform.itinerary.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;
public record DayVO(String id,String itineraryId,int dayNo,LocalDate travelDate,String title,String notes,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt,List<ItemVO> items) {
    public DayVO {items=List.copyOf(items);}
}
