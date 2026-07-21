package com.diamend.darksea.relic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Undrowned Heart's server-free decision logic: lethality and cooldown. */
class UndrownedHeartServiceTest {

    @Test
    void aHitIsFatalOnlyWhenItReachesZeroHealth() {
        // 6 HP left, a 6-damage blow lands exactly on death.
        assertTrue(UndrownedHeartService.wouldBeFatal(6.0, 6.0));
        assertTrue(UndrownedHeartService.wouldBeFatal(2.0, 100.0));
        // Survivable blows are left alone.
        assertFalse(UndrownedHeartService.wouldBeFatal(6.0, 5.9));
        assertFalse(UndrownedHeartService.wouldBeFatal(20.0, 1.0));
    }

    @Test
    void cooldownGatesRepeatSaves() {
        long cooldown = 120_000L; // two minutes
        long lastSave = 1_000_000L;
        // A fresh attunement (never saved) is always ready.
        assertTrue(UndrownedHeartService.offCooldown(0L, 0L, cooldown));
        // Immediately after a save the Heart is spent.
        assertFalse(UndrownedHeartService.offCooldown(lastSave, lastSave, cooldown));
        assertFalse(UndrownedHeartService.offCooldown(lastSave + 119_999L, lastSave, cooldown));
        // Exactly at the boundary it can fire again.
        assertTrue(UndrownedHeartService.offCooldown(lastSave + cooldown, lastSave, cooldown));
        assertTrue(UndrownedHeartService.offCooldown(lastSave + 200_000L, lastSave, cooldown));
    }

    @Test
    void remainingSecondsCountsDownAndFloorsAtZero() {
        long cooldown = 120_000L;
        long lastSave = 500_000L;
        // Just saved: the full two minutes remain (rounded up).
        assertEquals(120, UndrownedHeartService.cooldownRemainingSeconds(lastSave, lastSave, cooldown));
        // Half a second in still shows 120s (ceil), a full 60s in shows 60.
        assertEquals(60, UndrownedHeartService.cooldownRemainingSeconds(lastSave + 60_000L, lastSave, cooldown));
        // Ready → zero, never negative.
        assertEquals(0, UndrownedHeartService.cooldownRemainingSeconds(lastSave + cooldown, lastSave, cooldown));
        assertEquals(0, UndrownedHeartService.cooldownRemainingSeconds(lastSave + 999_999L, lastSave, cooldown));
    }
}
