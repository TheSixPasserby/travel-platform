package com.example.travelplatform.itinerary.dto;

import java.util.List;

public record GuideTripImportRequest(String clientRequestId, String guideId, List<String> destinationIds,
                                     String itineraryId, String dayId, NewItinerary newItinerary) {
    public record NewItinerary(String title, String startDate, String endDate, String travelDate) { }
}
