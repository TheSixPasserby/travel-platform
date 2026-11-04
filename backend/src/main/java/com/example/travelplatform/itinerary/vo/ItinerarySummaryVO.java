package com.example.travelplatform.itinerary.vo;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
public record ItinerarySummaryVO(String id,String title,ItineraryDestination destination,LocalDate startDate,LocalDate endDate,
        String status,long dayCount,long itemCount,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime createdAt,
        @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSXXX") OffsetDateTime updatedAt) { }
