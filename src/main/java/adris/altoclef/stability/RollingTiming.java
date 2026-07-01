package adris.altoclef.stability;

import java.util.Arrays;

public final class RollingTiming {
    private final long[] values;
    private int size;
    private int cursor;

    public RollingTiming(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        values = new long[capacity];
    }

    public synchronized void add(long nanos) {
        values[cursor] = nanos;
        cursor = (cursor + 1) % values.length;
        if (size < values.length) size++;
    }

    public synchronized Stats stats() {
        if (size == 0) return new Stats(0, 0, 0, 0);
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return new Stats(size, percentile(sorted, 0.50), percentile(sorted, 0.95), sorted[size - 1]);
    }

    public synchronized void clear() {
        Arrays.fill(values, 0);
        size = 0;
        cursor = 0;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    public record Stats(int samples, long medianNanos, long p95Nanos, long maxNanos) {
    }
}
