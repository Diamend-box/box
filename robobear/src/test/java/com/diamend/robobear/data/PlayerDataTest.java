package com.diamend.robobear.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record a run leaves behind. Pure — no server needed.
 */
class PlayerDataTest {

    private static final long NOW = 5_000_000L;

    private static PlayerData fresh() {
        return new PlayerData(UUID.randomUUID());
    }

    @Test
    @DisplayName("a fresh record is empty and clean")
    void startsEmpty() {
        PlayerData data = fresh();

        assertEquals(0, data.runs());
        assertEquals(0, data.bestRound());
        assertEquals(0, data.totalMilestones());
        assertFalse(data.isDirty(), "nothing has happened, so there's nothing to save");
    }

    @Test
    @DisplayName("the deepest round only ever goes up")
    void bestRoundRatchets() {
        PlayerData data = fresh();

        data.recordRound(3, NOW);
        assertEquals(3, data.bestRound());

        data.recordRound(7, NOW);
        assertEquals(7, data.bestRound());

        data.recordRound(2, NOW);
        assertEquals(7, data.bestRound(), "a worse run must not lower the record");
        assertEquals(3, data.totalRounds(), "but every round still counts toward the total");
    }

    @Test
    @DisplayName("milestones count per round and in total")
    void milestonesAreCountedPerRound() {
        PlayerData data = fresh();

        data.recordMilestone(5);
        data.recordMilestone(5);
        data.recordMilestone(10);

        assertEquals(2, data.milestoneCount(5));
        assertEquals(1, data.milestoneCount(10));
        assertEquals(0, data.milestoneCount(15));
        assertEquals(3, data.totalMilestones());
        assertTrue(data.hasMilestone(5));
        assertFalse(data.hasMilestone(15));
        assertEquals(10, data.deepestMilestone());
    }

    @Test
    @DisplayName("a run that cleared nothing doesn't set a fastest time")
    void emptyRunSetsNoRecord() {
        PlayerData data = fresh();

        data.recordRunEnd(0, 12, NOW);

        assertEquals(1, data.runs());
        assertEquals(0, data.bestRunSeconds(),
                "dying on round one is not the fastest run ever");
    }

    @Test
    @DisplayName("the fastest run is the shortest that cleared something")
    void fastestRunTracksTheMinimum() {
        PlayerData data = fresh();

        data.recordRunEnd(3, 600, NOW);
        assertEquals(600, data.bestRunSeconds());

        data.recordRunEnd(3, 900, NOW);
        assertEquals(600, data.bestRunSeconds(), "a slower run doesn't replace the record");

        data.recordRunEnd(3, 450, NOW);
        assertEquals(450, data.bestRunSeconds());
    }

    @Test
    @DisplayName("no cooldown configured means never waiting")
    void zeroCooldownNeverBlocks() {
        PlayerData data = fresh();
        data.recordRunEnd(1, 60, NOW);

        assertEquals(0, data.cooldownRemaining(0, NOW));
        assertEquals(0, data.cooldownRemaining(0, NOW + 1));
    }

    @Test
    @DisplayName("the cooldown counts down and then clears")
    void cooldownCountsDown() {
        PlayerData data = fresh();
        data.recordRunEnd(1, 60, NOW);

        assertEquals(60, data.cooldownRemaining(60, NOW));
        assertEquals(30, data.cooldownRemaining(60, NOW + 30_000L));
        assertEquals(0, data.cooldownRemaining(60, NOW + 60_000L));
        assertEquals(0, data.cooldownRemaining(60, NOW + 90_000L),
                "a cooldown never goes negative");
    }

    @Test
    @DisplayName("a player who has never run isn't on cooldown")
    void neverRunIsNeverOnCooldown() {
        assertEquals(0, fresh().cooldownRemaining(600, NOW));
    }

    @Test
    @DisplayName("clearing wipes everything and marks it for saving")
    void clearWipes() {
        PlayerData data = fresh();
        data.recordRound(4, NOW);
        data.recordMilestone(5);
        data.clean();

        data.clear();

        assertEquals(0, data.bestRound());
        assertEquals(0, data.totalMilestones());
        assertTrue(data.isDirty(), "a wipe has to reach disk");
    }
}
