package adris.altoclef.stability;

import java.util.ArrayDeque;
import java.util.Deque;

public final class DropSuppression<K> {
    private final int capacity;
    private final long durationTicks;
    private final double radiusSquared;
    private final Deque<Entry<K>> entries = new ArrayDeque<>();

    public DropSuppression(int capacity, long durationTicks, double radius) {
        this.capacity = capacity;
        this.durationTicks = durationTicks;
        radiusSquared = radius * radius;
    }

    public synchronized void record(K key, Position position, long tick) {
        purge(tick);
        if (entries.size() == capacity) entries.removeFirst();
        entries.addLast(new Entry<>(key, position, tick + durationTicks));
    }

    public synchronized boolean isSuppressed(K key, Position position, long tick, boolean requiredNow) {
        if (requiredNow) return false;
        purge(tick);
        return entries.stream().anyMatch(entry -> entry.key().equals(key)
                && entry.position().distanceSquared(position) <= radiusSquared);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized int size(long tick) {
        purge(tick);
        return entries.size();
    }

    private void purge(long tick) {
        entries.removeIf(entry -> entry.expiresAtTick() <= tick);
    }

    private record Entry<K>(K key, Position position, long expiresAtTick) {
    }

    public record Position(double x, double y, double z) {
        double distanceSquared(Position other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
