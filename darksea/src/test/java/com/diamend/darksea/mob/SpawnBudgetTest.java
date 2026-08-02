package com.diamend.darksea.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that makes clearing an island mean something. The first live test
 * found islands were endless farms — the per-island cap only limits how many
 * stand at once, so every kill freed its slot immediately.
 */
class SpawnBudgetTest {

    private static final long MINUTE = 60_000L;

    @Test
    void anIslandGivesItsBudgetAndThenNothing() {
        SpawnBudget budget = new SpawnBudget(3, 20 * MINUTE);
        long t = 1_000L;
        assertTrue(budget.tryConsume(t));
        assertTrue(budget.tryConsume(t));
        assertTrue(budget.tryConsume(t));
        assertFalse(budget.tryConsume(t), "a spent island must stop spawning");
        assertTrue(budget.exhausted(t));
        assertEquals(0, budget.remaining(t));
    }

    @Test
    void killingMobsNeverHandsBudgetBack() {
        // There is deliberately no "a mob died" entry point: the budget is
        // spent at spawn and only time returns it. This test exists to fail
        // loudly if someone adds one.
        SpawnBudget budget = new SpawnBudget(2, 20 * MINUTE);
        budget.tryConsume(0L);
        budget.tryConsume(0L);
        assertTrue(budget.exhausted(5 * MINUTE), "budget came back without the clock");
    }

    @Test
    void theClockRunsFromTheFirstSpawnNotTheLast() {
        SpawnBudget budget = new SpawnBudget(3, 20 * MINUTE);
        budget.tryConsume(0L);
        budget.tryConsume(10 * MINUTE);
        budget.tryConsume(15 * MINUTE);
        assertTrue(budget.exhausted(19 * MINUTE), "refilled early");
        // 20 minutes after the FIRST spawn, not the last — half-clearing an
        // island must never push its recovery further away.
        assertFalse(budget.exhausted(20 * MINUTE), "should refill at first-touch + 20");
        assertEquals(3, budget.remaining(20 * MINUTE));
    }

    @Test
    void aRefilledIslandStartsItsClockAgainOnTheNextSpawn() {
        SpawnBudget budget = new SpawnBudget(1, 10 * MINUTE);
        assertTrue(budget.tryConsume(0L));
        assertFalse(budget.tryConsume(5 * MINUTE));
        assertTrue(budget.tryConsume(10 * MINUTE), "did not refill on schedule");
        assertFalse(budget.tryConsume(15 * MINUTE), "second cycle did not re-arm");
        assertTrue(budget.tryConsume(20 * MINUTE));
    }

    @Test
    void theCountdownReadsZeroWhileTheIslandStillHasBudget() {
        SpawnBudget budget = new SpawnBudget(2, 10 * MINUTE);
        budget.tryConsume(0L);
        assertEquals(0L, budget.millisUntilRefill(MINUTE), "not spent yet — nothing to wait for");
        budget.tryConsume(MINUTE);
        assertEquals(9 * MINUTE, budget.millisUntilRefill(MINUTE));
        assertEquals(0L, budget.millisUntilRefill(30 * MINUTE), "overdue must not go negative");
    }

    @Test
    void resetHandsTheIslandStraightBack() {
        SpawnBudget budget = new SpawnBudget(2, 30 * MINUTE);
        budget.tryConsume(0L);
        budget.tryConsume(0L);
        budget.reset();
        assertEquals(2, budget.remaining(0L));
        assertTrue(budget.tryConsume(0L));
    }

    @Test
    void aNonsenseBudgetStillBehaves() {
        SpawnBudget budget = new SpawnBudget(0, -5);
        assertTrue(budget.tryConsume(0L), "a budget is always at least one");
        assertTrue(budget.tryConsume(0L), "a zero refill window means always ready");
    }
}
