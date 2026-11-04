package com.example.travelplatform.itinerary;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.itinerary.validation.ItineraryDates;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.*;

class ItineraryDatesTest {
    private void invalid(Runnable action){assertThatThrownBy(action::run).isInstanceOf(ApiException.class);}

    @Test void strictGregorianDatesKeepTheirCalendarDateAtMysqlLimits(){
        for(String value:new String[]{"1000-01-01","2024-02-29","9999-12-31"})assertThat(ItineraryDates.date(value).toString()).isEqualTo(value);
    }
    @Test void malformedImpossibleAndOutOfMysqlRangeDatesAreRejected(){
        for(String value:new String[]{"0999-12-31","0000-01-01","10000-01-01","+02024-01-01","+01000-01-01","2023-02-29","2024-02-30","2024-13-01","2024-1-01"," 2024-01-01","2024-01-01T00:00:00Z","","2024-01-01 "})invalid(()->ItineraryDates.date(value));
        invalid(()->ItineraryDates.date(null));
    }
    @Test void rangesIncludeBothEndsAndAllowAtMost366Days(){
        var same=ItineraryDates.range("2026-11-03","2026-11-03");assertThat(same.startDate()).isEqualTo(same.endDate());
        var leap=ItineraryDates.range("2024-01-01","2024-12-31");assertThat(leap.endDate()).isEqualTo(LocalDate.of(2024,12,31));
        invalid(()->ItineraryDates.range("2024-01-01","2025-01-01"));
        invalid(()->ItineraryDates.range("2026-11-04","2026-11-03"));
        invalid(()->ItineraryDates.range(null,"2026-11-03"));
    }
    @Test void dayNumbersAreAnchoredToTheStartAndOutsideDaysAreRejected(){
        LocalDate start=LocalDate.of(2024,2,28),end=LocalDate.of(2024,3,1);
        assertThat(ItineraryDates.dayNo(start,end,start)).isEqualTo(1);
        assertThat(ItineraryDates.dayNo(start,end,LocalDate.of(2024,2,29))).isEqualTo(2);
        assertThat(ItineraryDates.dayNo(start,end,end)).isEqualTo(3);
        invalid(()->ItineraryDates.dayNo(start,end,start.minusDays(1)));
        invalid(()->ItineraryDates.dayNo(start,end,end.plusDays(1)));
        invalid(()->ItineraryDates.dayNo(start,end,null));
        invalid(()->ItineraryDates.dayNo(LocalDate.of(999,1,1),LocalDate.of(999,1,2),LocalDate.of(999,1,1)));
    }
    @Test void timesAreMinutePrecisionAndOptionalWithoutSilentTrimming(){
        assertThat(ItineraryDates.time(null)).isNull();
        assertThat(ItineraryDates.time("00:00")).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(ItineraryDates.time("23:59")).isEqualTo(LocalTime.of(23,59));
        for(String value:new String[]{"","9:00","24:00","12:60","12:00:01"," 12:00","12:00Z","-01:00"})invalid(()->ItineraryDates.time(value));
    }
    @Test void pairedTimesAllowEqualityAndOptionalBoundsButRejectInferredOvernight(){
        assertThat(ItineraryDates.times(null,null).startTime()).isNull();
        assertThat(ItineraryDates.times("09:00",null).startTime()).isEqualTo(LocalTime.of(9,0));
        assertThat(ItineraryDates.times(null,"10:00").endTime()).isEqualTo(LocalTime.of(10,0));
        assertThat(ItineraryDates.times("09:00","09:00").endTime()).isEqualTo(LocalTime.of(9,0));
        invalid(()->ItineraryDates.times("23:00","01:00"));
    }
}
