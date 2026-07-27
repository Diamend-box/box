package com.diamend.darksea.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The black market's wall clock: stable within a cycle, honest about the wait. */
class MarketClockTest {

    private static final long TWO_HOURS = 2L * 60L * 60L * 1000L;

    @Test
    @DisplayName("two players at the same board in the same cycle see the same shelf")
    void theIndexIsStableAcrossACycle() {
        long start = 1_700_000_000_000L / TWO_HOURS * TWO_HOURS;
        long index = MarketClock.index(start, TWO_HOURS);
        assertEquals(index, MarketClock.index(start + 1L, TWO_HOURS));
        assertEquals(index, MarketClock.index(start + TWO_HOURS - 1L, TWO_HOURS));
        assertEquals(index + 1L, MarketClock.index(start + TWO_HOURS, TWO_HOURS));
    }

    @Test
    @DisplayName("a restart mid-cycle does not reroll the shelf")
    void theIndexNeedsNothingPersisted() {
        long now = 1_712_345_678_901L;
        assertEquals(MarketClock.index(now, TWO_HOURS), MarketClock.index(now, TWO_HOURS));
    }

    @Test
    @DisplayName("the index only ever moves forwards")
    void theIndexIsMonotonic() {
        long previous = Long.MIN_VALUE;
        for (long now = 0L; now < 40L * TWO_HOURS; now += TWO_HOURS / 7L) {
            long index = MarketClock.index(now, TWO_HOURS);
            assertTrue(index >= previous, "index went backwards at " + now);
            previous = index;
        }
    }

    @Test
    @DisplayName("the countdown never reads zero while the old stock is still up")
    void theCountdownStaysPositive() {
        for (long offset = 0L; offset < TWO_HOURS; offset += 1_000L) {
            long left = MarketClock.millisUntilNext(offset, TWO_HOURS);
            assertTrue(left > 0L && left <= TWO_HOURS, "bad countdown at " + offset);
        }
        assertEquals(TWO_HOURS, MarketClock.millisUntilNext(0L, TWO_HOURS));
        assertEquals(1L, MarketClock.millisUntilNext(TWO_HOURS - 1L, TWO_HOURS));
    }

    @Test
    @DisplayName("the countdown hits zero exactly when the index turns over")
    void theCountdownAgreesWithTheIndex() {
        long base = 5L * TWO_HOURS;
        for (long offset = 1L; offset < TWO_HOURS; offset += 997L) {
            long now = base + offset;
            assertEquals(MarketClock.index(now, TWO_HOURS) + 1L,
                    MarketClock.index(now + MarketClock.millisUntilNext(now, TWO_HOURS), TWO_HOURS));
        }
    }

    @Test
    @DisplayName("an absurd interval is floored rather than dividing by zero")
    void theIntervalHasAFloor() {
        assertEquals(MarketClock.MIN_INTERVAL_MILLIS, MarketClock.intervalMillis(0.0));
        assertEquals(MarketClock.MIN_INTERVAL_MILLIS, MarketClock.intervalMillis(-4.0));
        assertEquals(MarketClock.MIN_INTERVAL_MILLIS, MarketClock.intervalMillis(Double.NaN));
        assertEquals(TWO_HOURS, MarketClock.intervalMillis(2.0));
        assertTrue(MarketClock.index(1L, 0L) >= 0L);
    }

    @Test
    @DisplayName("the countdown reads the way a player would say it")
    void theCountdownFormatsReadably() {
        assertEquals("12s", MarketClock.format(12_400L));
        assertEquals("0s", MarketClock.format(-5L));
        assertEquals("1m", MarketClock.format(60_000L));
        assertEquals("2m", MarketClock.format(61_000L));
        assertEquals("59m", MarketClock.format(59L * 60_000L));
        assertEquals("1h", MarketClock.format(60L * 60_000L));
        assertEquals("1h 43m", MarketClock.format(103L * 60_000L));
        assertEquals("2h", MarketClock.format(TWO_HOURS));
    }

    @Test
    @DisplayName("the shipped shelf turns over four times as often as the sea resets")
    void theShippedIntervalIsTwoHours() {
        assertEquals(TWO_HOURS, MarketClock.intervalMillis(ShopStock.DEFAULT_ROTATION_HOURS));
    }
}
