package com.diamend.boxcore.boost;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One running multiplier, with a wall-clock end time.
 *
 * <p>Expiry is an absolute timestamp rather than a countdown, so a boost is
 * worth the same whether the server ticks slowly, lags, or restarts underneath
 * it — thirty minutes is thirty minutes. That is also what lets a player boost
 * survive a relog without anyone having to tick it down.
 *
 * @param type       what this multiplies
 * @param multiplier how much, where 1.0 is no change
 * @param expiresAt  epoch millis at which it stops applying
 * @param source     where it came from, for display and for de-duplicating
 *                   scheduled windows (e.g. {@code admin}, {@code schedule:0})
 */
public record Boost(BoostType type, double multiplier, long expiresAt, String source) {

    /** A boost of this type and size, running for a duration from now. */
    public static Boost lasting(BoostType type, double multiplier, long millis, String source) {
        return new Boost(type, multiplier, System.currentTimeMillis() + Math.max(0, millis), source);
    }

    public boolean isActive(long now) {
        return multiplier > 0 && expiresAt > now;
    }

    public long remainingMillis(long now) {
        return Math.max(0, expiresAt - now);
    }

    /**
     * Applies a multiplier to a whole number of items.
     *
     * <p>A fractional multiplier cannot be honoured exactly on a single item —
     * 1.5× of one diamond is not a thing that can drop. Flooring would make
     * every fractional boost do nothing at all for the single-item drops that
     * most ore produces, so the fraction is paid as a chance instead: 1.5× of
     * one item is one item, plus a second half the time. Over a session that
     * averages out to exactly the multiplier.
     *
     * @param roll a value in [0,1); the caller supplies it so this stays testable
     */
    public static long scale(long amount, double multiplier, double roll) {
        if (amount <= 0 || multiplier <= 0) {
            return 0;
        }
        if (multiplier == 1.0) {
            return amount;
        }
        double exact = amount * multiplier;
        long whole = (long) exact;
        return roll < exact - whole ? whole + 1 : whole;
    }

    /** {@link #scale(long, double, double)} with a real roll. */
    public static long scale(long amount, double multiplier) {
        return scale(amount, multiplier, ThreadLocalRandom.current().nextDouble());
    }
}
