package com.diamend.robobear.challenge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run's own bookkeeping: the clock, the Cogs, the round counter and the
 * position packing. All of it pure, so none of it needs a server.
 */
class RoboRunTest {

    private static final long NOW = 1_000_000L;

    private static RoboRun newRun() {
        return new RoboRun(UUID.randomUUID(), NOW, 10, 1);
    }

    private static Objective kills(int amount) {
        return new Objective(ObjectiveType.KILL_MOBS, null, null, amount, 5);
    }

    @Test
    @DisplayName("a new run starts on round one, choosing, with its starting Cogs")
    void startsChoosing() {
        RoboRun run = newRun();

        assertEquals(RoboRun.State.CHOOSING, run.state());
        assertEquals(1, run.round());
        assertEquals(10, run.cogs());
        assertEquals(1, run.rerollsLeft());
    }

    @Test
    @DisplayName("the clock only runs once an objective is taken")
    void clockStartsWithTheObjective() {
        RoboRun run = newRun();

        assertEquals(0, run.secondsLeft(NOW), "no clock while choosing");
        assertFalse(run.isExpired(NOW + 999_999L), "a run that hasn't begun can't time out");

        run.begin(kills(5), 60, NOW);

        assertEquals(RoboRun.State.RUNNING, run.state());
        assertEquals(60, run.secondsLeft(NOW));
        assertEquals(30, run.secondsLeft(NOW + 30_000L));
        assertFalse(run.isExpired(NOW + 59_000L));
        assertTrue(run.isExpired(NOW + 60_000L));
    }

    @Test
    @DisplayName("finishing a round stops the clock and opens the shop")
    void finishingStopsTheClock() {
        RoboRun run = newRun();
        run.begin(kills(2), 60, NOW);

        assertFalse(run.count(), "one of two is not done");
        assertTrue(run.count(), "two of two completes the round");

        run.finishRound();

        assertEquals(RoboRun.State.SHOPPING, run.state());
        assertEquals(0, run.secondsLeft(NOW), "the clock is stopped between rounds");
        assertFalse(run.isExpired(NOW + 999_999L),
                "a run parked in the shop must never time out");
    }

    @Test
    @DisplayName("rounds advance and progress resets")
    void roundsAdvance() {
        RoboRun run = newRun();
        run.begin(kills(3), 60, NOW);
        run.count();
        assertEquals(1, run.progress());

        run.finishRound();
        run.nextRound();
        run.begin(kills(3), 60, NOW + 1000L);

        assertEquals(2, run.round());
        assertEquals(0, run.progress(), "a new round starts from zero");
    }

    @Test
    @DisplayName("Cogs can't be overspent")
    void cogsCannotGoNegative() {
        RoboRun run = newRun();

        assertFalse(run.spendCogs(11), "spending more than you hold is refused");
        assertEquals(10, run.cogs(), "a refused purchase costs nothing");

        assertTrue(run.spendCogs(4));
        assertEquals(6, run.cogs());

        run.addCogs(9);
        assertEquals(15, run.cogs());
    }

    @Test
    @DisplayName("a bought battery extends the round already running")
    void overclockExtendsTheCurrentRound() {
        RoboRun run = newRun();
        run.begin(kills(5), 60, NOW);
        assertEquals(60, run.secondsLeft(NOW));

        run.addBonusSeconds(30);

        assertEquals(90, run.secondsLeft(NOW),
                "time bought mid-round should be felt in that round");
        assertEquals(30, run.bonusSeconds());
    }

    @Test
    @DisplayName("bonus seconds apply to later rounds too")
    void overclockCarriesToLaterRounds() {
        RoboRun run = newRun();
        run.addBonusSeconds(30);
        run.begin(kills(5), 60, NOW);

        assertEquals(90, run.secondsLeft(NOW));
    }

    @Test
    @DisplayName("rerolls run out and can't go negative")
    void rerollsAreFinite() {
        RoboRun run = newRun();

        assertTrue(run.useReroll());
        assertEquals(0, run.rerollsLeft());
        assertFalse(run.useReroll(), "there was only one");
        assertEquals(0, run.rerollsLeft());
    }

    @Test
    @DisplayName("upgrade levels stack")
    void upgradesStack() {
        RoboRun run = newRun();

        assertEquals(0, run.levelOf(Upgrade.HASTE));
        run.addLevel(Upgrade.HASTE);
        run.addLevel(Upgrade.HASTE);
        assertEquals(2, run.levelOf(Upgrade.HASTE));
        assertEquals(0, run.levelOf(Upgrade.SALVAGE), "levels don't leak between upgrades");
    }

    @Test
    @DisplayName("progress fraction is clamped to one")
    void fractionIsClamped() {
        RoboRun run = newRun();
        run.begin(kills(2), 60, NOW);

        assertEquals(0.0, run.fraction(), 1.0e-9);
        run.count();
        assertEquals(0.5, run.fraction(), 1.0e-9);
        run.count();
        run.count();
        assertEquals(1.0, run.fraction(), 1.0e-9, "over-counting never exceeds a full bar");
    }

    // ------------------------------------------------------------------
    // Position packing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("distinct positions pack to distinct longs")
    void packingIsInjective() {
        Set<Long> seen = new HashSet<>();
        int collisions = 0;

        for (int x = -300; x <= 300; x += 37) {
            for (int y = -64; y <= 320; y += 29) {
                for (int z = -300; z <= 300; z += 37) {
                    if (!seen.add(RoboRun.pack(x, y, z))) {
                        collisions++;
                    }
                }
            }
        }
        assertEquals(0, collisions,
                "two different blocks packing to the same long would let one launder the other");
    }

    @Test
    @DisplayName("packing covers the real world height range")
    void packingCoversWorldHeight() {
        assertNotEquals(RoboRun.pack(0, -64, 0), RoboRun.pack(0, 320, 0));
        assertNotEquals(RoboRun.pack(0, -64, 0), RoboRun.pack(0, -63, 0));
        assertNotEquals(RoboRun.pack(0, 319, 0), RoboRun.pack(0, 320, 0));
    }
}
