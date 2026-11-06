package com.example.travelplatform.ai.dto;

import java.util.List;

public record AiRequirements(
        String cityId, String startDate, String endDate, Integer travelers,
        String budgetAmount, String budgetBasis, String currency, List<String> interests,
        String pace, String transport, List<String> preferredIds,
        List<String> mustVisitIds, List<String> avoidIds) {
}
