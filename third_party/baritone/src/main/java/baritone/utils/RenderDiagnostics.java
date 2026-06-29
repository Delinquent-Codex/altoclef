/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package baritone.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RenderDiagnostics {

    private static final AtomicLong BUFFERS_ALLOCATED = new AtomicLong();
    private static final AtomicLong TOTAL_CAPACITY_ALLOCATED = new AtomicLong();
    private static final AtomicLong VERTICES_DRAWN = new AtomicLong();
    private static final AtomicInteger LIVE_BUFFERS = new AtomicInteger();
    private static final AtomicInteger LIVE_CAPACITY = new AtomicInteger();
    private static final AtomicInteger MAX_LIVE_BUFFERS = new AtomicInteger();

    private RenderDiagnostics() {}

    static void onBufferAllocated(int capacity) {
        BUFFERS_ALLOCATED.incrementAndGet();
        TOTAL_CAPACITY_ALLOCATED.addAndGet(capacity);
        int live = LIVE_BUFFERS.incrementAndGet();
        LIVE_CAPACITY.addAndGet(capacity);
        MAX_LIVE_BUFFERS.accumulateAndGet(live, Math::max);
    }

    static void onBufferClosed(int capacity) {
        LIVE_BUFFERS.decrementAndGet();
        LIVE_CAPACITY.addAndGet(-capacity);
    }

    static void onMeshDrawn(int vertices) {
        VERTICES_DRAWN.addAndGet(vertices);
    }

    public static int liveBuffers() {
        return LIVE_BUFFERS.get();
    }

    public static String snapshot(long renderCalls, int listenerCount, int currentPathLength, int nextPathLength) {
        return "renderCalls=" + renderCalls
                + ", vertices=" + VERTICES_DRAWN.get()
                + ", buffers=" + BUFFERS_ALLOCATED.get()
                + ", allocatedCapacity=" + TOTAL_CAPACITY_ALLOCATED.get()
                + ", liveBuffers=" + LIVE_BUFFERS.get()
                + ", liveCapacity=" + LIVE_CAPACITY.get()
                + ", maxLiveBuffers=" + MAX_LIVE_BUFFERS.get()
                + ", handlers=" + listenerCount
                + ", currentPath=" + currentPathLength
                + ", nextPath=" + nextPathLength;
    }
}
