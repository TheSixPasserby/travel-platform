package com.example.travelplatform.ai.dto;

import java.util.List;

public record AiDayRequest(AiRequirements requirements, String targetDate, List<String> usedDestinationIds) {
}
