package com.diamend.anticheat.violation;

import java.util.EnumMap;
import java.util.Map;

import com.diamend.anticheat.check.CheckType;

/**
 * Holds one player's violation levels, one accumulator per {@link CheckType}.
 *
 * <p>Violation level (VL) is a decaying score: a flag adds to it, and it bleeds
 * off over time so that a player who triggers a check once an hour never
 * accumulates a punishable total. All state here is plain numbers and time
 * stamps, so this class is fully unit-testable without a server.
 */
public final class ViolationTracker {

    private final Map<CheckType, Double> levels = new EnumMap<>(CheckType.class);
    private final Map<CheckType, Long> lastUpdate = new EnumMap<>(CheckType.class);

    /** Violation points removed per second of elapsed time. */
    private final double decayPerSecond;

    public ViolationTracker(double decayPerSecond) {
        this.decayPerSecond = Math.max(0.0D, decayPerSecond);
    }

    /**
     * Apply decay for the time elapsed since this check was last touched, add
     * {@code amount}, and return the new violation level.
     *
     * @param now current time in milliseconds
     */
    public double add(CheckType check, double amount, long now) {
        double current = decayedLevel(check, now);
        double updated = Math.max(0.0D, current + amount);
        levels.put(check, updated);
        lastUpdate.put(check, now);
        return updated;
    }

    /** Current violation level for a check, after applying decay up to {@code now}. */
    public double level(CheckType check, long now) {
        return decayedLevel(check, now);
    }

    /** Reset every check's violation level for this player back to zero. */
    public void reset() {
        levels.clear();
        lastUpdate.clear();
    }

    /** Reset a single check's violation level. */
    public void reset(CheckType check) {
        levels.remove(check);
        lastUpdate.remove(check);
    }

    private double decayedLevel(CheckType check, long now) {
        Double stored = levels.get(check);
        if (stored == null) {
            return 0.0D;
        }
        Long last = lastUpdate.get(check);
        if (last == null) {
            return stored;
        }
        double elapsedSeconds = Math.max(0L, now - last) / 1000.0D;
        double decayed = stored - elapsedSeconds * decayPerSecond;
        return Math.max(0.0D, decayed);
    }
}
