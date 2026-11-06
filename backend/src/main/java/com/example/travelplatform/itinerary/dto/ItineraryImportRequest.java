package com.example.travelplatform.itinerary.dto;

import java.util.List;

/** New itinerary only; no client ownership, parent IDs or persisted sort positions. */
public record ItineraryImportRequest(String clientRequestId,String title,String destinationId,
        String startDate,String endDate,String notes,List<Day> days) {
    public record Day(String travelDate,String title,String notes,List<Item> items) { }
    public record Item(String kind,String destinationId,String placeName,String addressText,
            String startTime,String endTime,String notes) { }
}
