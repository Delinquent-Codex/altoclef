package adris.altoclef.stability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class BoundedHistory<T> {
    private final int capacity;
    private final Deque<T> values;

    public BoundedHistory(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        values = new ArrayDeque<>(capacity);
    }

    public synchronized void add(T value) {
        if (values.size() == capacity) {
            values.removeFirst();
        }
        values.addLast(value);
    }

    public synchronized List<T> snapshot() {
        return new ArrayList<>(values);
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized void clear() {
        values.clear();
    }
}
