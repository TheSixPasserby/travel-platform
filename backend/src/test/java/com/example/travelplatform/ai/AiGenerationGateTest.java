package com.example.travelplatform.ai;
import com.example.travelplatform.ai.service.AiGenerationGate;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class AiGenerationGateTest {
    static final class MutableClock extends Clock {
        final AtomicLong millis=new AtomicLong();
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(millis.get());}
        public long millis(){return millis.get();}
    }
    @Test void rollingBoundaryMustNotAllowFourCallsWithinTwoSeconds(){
        var clock=new MutableClock();var gate=new AiGenerationGate(clock);
        gate.acquire(1).close();clock.millis.set(59000);gate.acquire(1).close();
        clock.millis.set(60000);gate.acquire(1).close();clock.millis.set(60001);
        assertThrows(AiGenerationGate.Rejected.class,()->gate.acquire(1));
        clock.millis.set(119000);assertDoesNotThrow(()->gate.acquire(1).close());
    }
    @Test void globalAndPerUserConcurrencyReleaseExactlyOnce(){
        var gate=new AiGenerationGate(new MutableClock());var one=gate.acquire(1);var two=gate.acquire(2);
        assertThrows(AiGenerationGate.Rejected.class,()->gate.acquire(1));
        assertThrows(AiGenerationGate.Rejected.class,()->gate.acquire(3));
        one.close();one.close();var three=gate.acquire(3);
        assertThrows(AiGenerationGate.Rejected.class,()->gate.acquire(4));
        three.close();two.close();assertDoesNotThrow(()->gate.acquire(4).close());
    }
}
