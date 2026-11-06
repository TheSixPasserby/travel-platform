package com.example.travelplatform.ai.vo;

import com.example.travelplatform.itinerary.vo.ItineraryDestination;
import java.util.List;

public record AiPreview(
        String title, String summary, ItineraryDestination city,
        String startDate, String endDate, int travelers,
        String budgetAmount, String budgetBasis, String currency,
        List<Day> days, Budget budgetEstimate, List<String> warnings,
        List<Candidate> candidates, List<SourceGuide> sourceGuides) {
    public record Item(String kind, String destinationId, String placeName, String addressText,
                       String startTime, String endTime, String notes, String estimatedCost) { }
    public record Day(int dayNo, String travelDate, String title, List<Item> items) { }
    public record Budget(String currency, String perPerson, String total, String notes) { }
    public record Candidate(String id, String name, String address, String coverImageUrl,
                            Integer recommendedDurationMinutes, String openingHours, String ticketInfo,
                            String sourceUrl, String verifiedAt) { }
    public record SourceGuide(String id, String title) { }
    public record DayPreview(Day day, List<String> warnings) { }
    public record Capabilities(boolean enabled, boolean available, String reason, int maxDays,
                               String provider, String providerLabel) { }
}
