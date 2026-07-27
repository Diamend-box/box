package com.diamend.darksea.npc;

/**
 * The black market's own clock.
 *
 * <p>His shelf used to be seeded off the sea's reset counter, which tied it to
 * a six-hour cycle: a player with a two-hour session saw exactly one shelf,
 * ever, and never had a reason to walk back. Here the shelf runs on wall time
 * instead, on whatever interval {@code shops.yml} asks for.
 *
 * <p>The index is simply how many whole intervals have elapsed since the epoch,
 * so it is the same number on every server tick, survives a restart without
 * rerolling, and needs nothing persisted. Two players standing at the same
 * board always see the same five lines.
 *
 * <p>Pure Java — no Bukkit, no {@code System.currentTimeMillis()} baked in.
 * The caller passes the clock reading, which is what makes the whole rotation
 * schedule assertable in a unit test.
 */
public final class MarketClock {

    /** Interval floor. A shelf that rerolled faster than this would flicker. */
    public static final long MIN_INTERVAL_MILLIS = 60_000L;

    private MarketClock() {
    }

    /** Milliseconds in {@code hours}, never below {@link #MIN_INTERVAL_MILLIS}. */
    public static long intervalMillis(double hours) {
        double millis = hours * 60.0 * 60.0 * 1000.0;
        if (!Double.isFinite(millis) || millis < MIN_INTERVAL_MILLIS) {
            return MIN_INTERVAL_MILLIS;
        }
        return (long) millis;
    }

    /**
     * The rotation index for a moment in time — the seed the shelf is drawn
     * from. Monotonic: it never goes backwards as the clock advances.
     */
    public static long index(long nowMillis, long intervalMillis) {
        long interval = Math.max(MIN_INTERVAL_MILLIS, intervalMillis);
        return Math.floorDiv(nowMillis, interval);
    }

    /**
     * Milliseconds until the shelf next changes. Always in
     * {@code (0, intervalMillis]} — at the exact instant of a rotation this
     * reports a whole interval rather than zero, so the countdown a player is
     * looking at never reads "0s" while the old stock is still on the board.
     */
    public static long millisUntilNext(long nowMillis, long intervalMillis) {
        long interval = Math.max(MIN_INTERVAL_MILLIS, intervalMillis);
        long elapsed = Math.floorMod(nowMillis, interval);
        return interval - elapsed;
    }

    /**
     * A countdown as a player should read it: {@code "1h 43m"}, {@code "43m"},
     * {@code "12s"}. Minutes are rounded up while any part of one remains, so
     * the last minute reads "1m" rather than "0m" before it flips to seconds.
     */
    public static String format(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = (seconds + 59L) / 60L;
        if (minutes < 60L) {
            return minutes + "m";
        }
        long hours = minutes / 60L;
        long rest = minutes % 60L;
        return rest == 0L ? hours + "h" : hours + "h " + rest + "m";
    }
}
