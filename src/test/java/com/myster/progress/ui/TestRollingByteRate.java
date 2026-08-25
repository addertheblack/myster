package com.myster.progress.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;

class TestRollingByteRate {
    private final TestNanoClock clock = new TestNanoClock();
    private final RollingByteRate rate = new RollingByteRate(clock);

    @Test
    void calculatesRateFromRecentActiveSamples() {
        assertEquals(0, rate.update(1_000));

        clock.advance(Duration.ofMillis(250));

        assertEquals(4_000, rate.update(2_000));
    }

    @Test
    void usesApproximatelyLastSecondAsWindowAdvances() {
        assertEquals(0, rate.update(0));
        advanceAndUpdate(250, 250);
        advanceAndUpdate(250, 500);
        advanceAndUpdate(250, 750);
        advanceAndUpdate(250, 1_000);

        clock.advance(Duration.ofMillis(250));

        assertEquals(2_000, rate.update(2_250));
    }

    @Test
    void calculatesRateAcrossSparseEvents() {
        assertEquals(0, rate.update(0));

        clock.advance(Duration.ofSeconds(2));

        assertEquals(1_024, rate.update(2_048));
        clock.advance(Duration.ofSeconds(2));
        assertEquals(1_024, rate.update(4_096));
    }

    @Test
    void resetClearsPreviousSamples() {
        assertEquals(0, rate.update(0));
        advanceAndUpdate(100, 100);

        rate.reset();
        clock.advance(Duration.ofMillis(100));

        assertEquals(0, rate.update(10_000));
        clock.advance(Duration.ofMillis(100));
        assertEquals(1_000, rate.update(10_100));
    }

    @Test
    void byteCountRegressionStartsNewSamplingPeriod() {
        assertEquals(0, rate.update(1_000));
        advanceAndUpdate(100, 1_100);

        clock.advance(Duration.ofMillis(100));

        assertEquals(0, rate.update(50));
        clock.advance(Duration.ofMillis(100));
        assertEquals(1_000, rate.update(150));
    }

    @Test
    void clampsRatesToDownloadItemRange() {
        assertEquals(0, rate.update(0));

        clock.advance(Duration.ofNanos(1));

        assertEquals(Integer.MAX_VALUE, rate.update(Long.MAX_VALUE));
    }

    private void advanceAndUpdate(long millis, long cumulativeBytes) {
        clock.advance(Duration.ofMillis(millis));
        rate.update(cumulativeBytes);
    }

    private static final class TestNanoClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }

        void advance(Duration duration) {
            now += duration.toNanos();
        }
    }
}
