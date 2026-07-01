package adris.altoclef.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RollingTimingTest {
    @Test
    void reportsMedianP95AndMaximumOverBoundedWindow() {
        RollingTiming timing = new RollingTiming(4);
        timing.add(1);
        timing.add(2);
        timing.add(3);
        timing.add(100);

        RollingTiming.Stats stats = timing.stats();
        assertEquals(2, stats.medianNanos());
        assertEquals(100, stats.p95Nanos());
        assertEquals(100, stats.maxNanos());

        timing.add(4);
        assertEquals(3, timing.stats().medianNanos());
        assertEquals(100, timing.stats().maxNanos());
        timing.add(5);
        timing.add(6);
        timing.add(7);
        assertEquals(7, timing.stats().maxNanos());
    }
}
