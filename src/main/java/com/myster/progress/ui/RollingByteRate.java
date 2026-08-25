package com.myster.progress.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Calculates bytes per second from cumulative byte samples over an approximately one-second
 * active window. When samples arrive less often than once per second, the most recent two samples
 * are used so low-throughput transfers still produce a rate. A reset, a regressing byte count, or
 * a non-increasing clock reading starts a new sampling period. The first sample in a period returns
 * zero; results larger than the download manager's {@code int} speed model are clamped. Instances
 * are mutable and must be confined to one thread; the download manager uses the EDT.
 */
final class RollingByteRate {
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final LongSupplier nanoTime;
    private final Deque<Sample> samples = new ArrayDeque<>();

    RollingByteRate() {
        this(System::nanoTime);
    }

    /**
     * @param nanoTime monotonic time source returning nanoseconds
     */
    RollingByteRate(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    /**
     * Records a cumulative byte count and returns the recent rate in bytes per second.
     *
     * @param cumulativeBytes total bytes downloaded at the current sampling instant
     * @return the recent bytes per second, or zero until an active baseline is available
     */
    int update(long cumulativeBytes) {
        long now = nanoTime.getAsLong();
        Sample latest = samples.peekLast();
        if (latest == null
                || cumulativeBytes < latest.cumulativeBytes()
                || now <= latest.timeNanos()) {
            restart(now, cumulativeBytes);
            return 0;
        }

        Sample current = new Sample(now, cumulativeBytes);
        samples.addLast(current);

        long cutoff = now - WINDOW_NANOS;
        while (samples.size() > 1) {
            Sample next = secondSample();
            if (next.timeNanos() > cutoff) {
                break;
            }
            samples.removeFirst();
        }

        Sample baseline = samples.peekFirst();
        long elapsedNanos = current.timeNanos() - baseline.timeNanos();
        long downloadedBytes = current.cumulativeBytes() - baseline.cumulativeBytes();
        if (elapsedNanos <= 0 || downloadedBytes <= 0) {
            return 0;
        }

        double bytesPerSecond = (double) downloadedBytes * WINDOW_NANOS / elapsedNanos;
        return bytesPerSecond >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) bytesPerSecond;
    }

    /** Clears all samples so the next update establishes a new active baseline. */
    void reset() {
        samples.clear();
    }

    private void restart(long now, long cumulativeBytes) {
        samples.clear();
        samples.addLast(new Sample(now, cumulativeBytes));
    }

    private Sample secondSample() {
        var iterator = samples.iterator();
        iterator.next();
        return iterator.next();
    }

    private record Sample(long timeNanos, long cumulativeBytes) {}
}
