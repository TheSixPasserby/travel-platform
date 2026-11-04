package com.example.travelplatform.itinerary.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;
public record ItemVO(String id,String itineraryDayId,ItineraryDestination destination,String placeName,String addressText,
        String startTime,String endTime,String notes,int sortOrder,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt) { }
