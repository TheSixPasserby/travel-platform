package com.example.travelplatform.itinerary.vo;

import java.util.List;

public record GuideTripImportResult(String itineraryId, String dayId, List<String> added, List<String> alreadyPresent) { }
