package com.example.travelplatform.guide.validation;
import java.math.BigDecimal;
import java.util.List;
import com.example.travelplatform.guide.dto.GuideImageRequest;
public record GuideValues(String title, String content, String summary, Long destinationId, String coverImageUrl,
        Integer travelDays, BigDecimal budgetPerPerson, String season, List<Long> tagIds, List<GuideImageRequest> images) { }
