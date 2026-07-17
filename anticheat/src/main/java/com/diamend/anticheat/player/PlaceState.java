package com.diamend.anticheat.player;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Recent block-placement timing for the Scaffold check. Placements arrive on
 * Netty threads, so access is {@code synchronized}.
 */
public final class PlaceState {

    private static final int HISTORY = 12;

    private final Deque<Long> placeIntervals = new ArrayDeque<>(HISTORY);
    private long lastPlace;
    private boolean hasPlace;

    /** Record a placement and return the interval (ms) since the previous one, or -1. */
    public synchronized long recordPlace(long now) {
        long interval = hasPlace ? (now - lastPlace) : -1L;
        lastPlace = now;
        hasPlace = true;
        if (interval >= 0) {
            if (placeIntervals.size() >= HISTORY) {
                placeIntervals.pollFirst();
            }
            placeIntervals.addLast(interval);
        }
        return interval;
    }

    /** Snapshot of recent inter-placement intervals (ms), oldest first. */
    public synchronized long[] intervals() {
        long[] out = new long[placeIntervals.size()];
        int i = 0;
        for (long v : placeIntervals) {
            out[i++] = v;
        }
        return out;
    }
}
