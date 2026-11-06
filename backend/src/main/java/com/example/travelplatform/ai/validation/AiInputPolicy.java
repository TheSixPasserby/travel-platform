package com.example.travelplatform.ai.validation;

import com.example.travelplatform.ai.dto.AiRequirements;
import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.common.error.ErrorCode;
import com.example.travelplatform.common.validation.RequestValues;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class AiInputPolicy {
    private static final Set<String> INTERESTS = Set.of("FOOD","NATURE","CULTURE","PHOTOGRAPHY","FAMILY","HIKING","CITY_WALK");
    private static final Set<String> PACES = Set.of("RELAXED","BALANCED","COMPACT");
    private static final Set<String> TRANSPORTS = Set.of("PUBLIC","WALK","TAXI","MIXED");
    private AiInputPolicy() { }

    public record Input(long cityId, LocalDate start, LocalDate end, int travelers,
                        BigDecimal budgetAmount, String budgetBasis, List<String> interests,
                        String pace, String transport, Set<Long> preferred, Set<Long> must, Set<Long> avoid) {
        public int dayCount() { return Math.toIntExact(ChronoUnit.DAYS.between(start,end)+1); }
    }

    public static Input normalize(AiRequirements request) {
        if (request == null) throw invalid();
        long cityId = RequestValues.positiveId(request.cityId());
        var range = ItineraryDates.range(request.startDate(),request.endDate());
        if (ChronoUnit.DAYS.between(range.startDate(),range.endDate()) >= 14
                || request.travelers() == null || request.travelers() < 1 || request.travelers() > 20) throw invalid();
        if (!"CNY".equals(request.currency())
                || !("PER_PERSON".equals(request.budgetBasis()) || "TOTAL".equals(request.budgetBasis()))
                || request.pace() == null || !PACES.contains(request.pace())
                || request.transport() == null || !TRANSPORTS.contains(request.transport())) throw invalid();
        BigDecimal amount = amount(request.budgetAmount());
        if (request.interests() == null || request.interests().size() > 8
                || request.interests().stream().anyMatch(value -> value == null || !INTERESTS.contains(value))
                || new HashSet<>(request.interests()).size() != request.interests().size()) throw invalid();
        Set<Long> preferred = ids(request.preferredIds(),20);
        Set<Long> must = ids(request.mustVisitIds(),20);
        Set<Long> avoid = ids(request.avoidIds(),20);
        if (!Collections.disjoint(preferred,avoid) || !Collections.disjoint(must,avoid)) throw invalid();
        long dayCount = ChronoUnit.DAYS.between(range.startDate(),range.endDate()) + 1;
        if (must.size() > dayCount * 8) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "必须去的景点超过每日最多8项的行程容量，请减少景点或增加天数");
        }
        return new Input(cityId,range.startDate(),range.endDate(),request.travelers(),amount,
                request.budgetBasis(),List.copyOf(request.interests()),request.pace(),request.transport(),preferred,must,avoid);
    }

    public static Set<Long> ids(List<String> values, int maximum) {
        if (values == null) return Set.of();
        if (values.size() > maximum) throw invalid();
        Set<Long> result = new LinkedHashSet<>();
        for (String value : values) {
            if (!result.add(RequestValues.positiveId(value))) throw invalid();
        }
        return Collections.unmodifiableSet(result);
    }

    public static BigDecimal amount(String value) {
        if (value == null || !value.matches("(0|[1-9][0-9]{0,7})(\\.[0-9]{1,2})?")) throw invalid();
        return new BigDecimal(value).setScale(2);
    }

    private static ApiException invalid() { return new ApiException(ErrorCode.VALIDATION_FAILED); }
}
