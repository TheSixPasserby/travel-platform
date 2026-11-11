package com.example.travelplatform.guide;

import com.example.travelplatform.common.error.ApiException;
import com.example.travelplatform.guide.service.FeedCursor;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class FeedCursorTest {
    final LocalDateTime now=LocalDateTime.of(2026,11,11,17,0);
    final String key=FeedCursor.fingerprint("public/latest");
    final FeedCursor cursor=new FeedCursor(now,20,now.minusDays(1),12,0);

    @Test void roundTripPreservesOrderingAndSnapshot() {
        assertThat(FeedCursor.decode(cursor.encode(key),key,now)).isEqualTo(cursor);
    }

    @Test void futureAndCrossQueryCursorsHaveStableValidationErrors() {
        for(var time:new LocalDateTime[]{now.plusMinutes(1)}) {
            var raw=new FeedCursor(time,20,time.minusDays(1),12,0).encode(key);
            assertThatThrownBy(()->FeedCursor.decode(raw,key,now)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(()->FeedCursor.decode(cursor.encode(key),"another",now)).isInstanceOf(ApiException.class);
    }

    @Test void returningAfterAnHourCanContinueTheSamePublicSnapshot() {
        assertThat(FeedCursor.decode(cursor.encode(key),key,now.plusHours(1))).isEqualTo(cursor);
    }

    @Test void malformedDatesOversizedCursorsAndInvalidNumbersNeverProduceInternalErrors() {
        String badDate=Base64.getUrlEncoder().encodeToString(("invalid|20|invalid|12|0|"+key).getBytes(StandardCharsets.UTF_8));
        String negative=new FeedCursor(now,20,now.minusDays(1),-1,0).encode(key);
        for(String raw:new String[]{"",badDate,negative,"x".repeat(1025)})
            assertThatThrownBy(()->FeedCursor.decode(raw,key,now)).isInstanceOf(ApiException.class);
    }
}
