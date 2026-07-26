package com.diamend.darksea.world.cultist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tool curve. Every assertion here is a promise about how gear feels in the
 * caves, and none of them needs a server to check.
 */
class MiningSpeedTest {

    private static final double REF = MiningSpeed.speed(MiningSpeed.Tier.NETHERITE, 15, 0, 0);
    private static final double FLOOR = 0.4;

    private static double seconds(MiningSpeed.Tier tier, int eff, int haste, int fatigue) {
        return MiningSpeed.seconds(1.5, REF, MiningSpeed.speed(tier, eff, haste, fatigue), FLOOR);
    }

    /**
     * The contract the whole config rests on: a reference pickaxe takes exactly
     * the configured time. If this drifts, every number in ores.yml quietly
     * means something other than what it says.
     */
    @Test
    void referenceGearTakesExactlyTheConfiguredTime() {
        assertEquals(1.5, seconds(MiningSpeed.Tier.NETHERITE, 15, 0, 0), 1e-9);
        assertEquals(4.0, MiningSpeed.seconds(4.0, REF, REF, FLOOR), 1e-9);
    }

    @Test
    void onlyPickaxesResolveToATier() {
        assertNotNull(MiningSpeed.tierOf("NETHERITE_PICKAXE"));
        assertNotNull(MiningSpeed.tierOf("WOODEN_PICKAXE"));
        assertNull(MiningSpeed.tierOf("NETHERITE_SWORD"));
        assertNull(MiningSpeed.tierOf("NETHERITE_SHOVEL"));
        assertNull(MiningSpeed.tierOf("AIR"));
        assertNull(MiningSpeed.tierOf(null));
        assertNull(MiningSpeed.tierOf("PICKAXE"));
    }

    /** Worse metal is slower, every step of the way. */
    @Test
    void lowerTiersAreMonotonicallySlower() {
        MiningSpeed.Tier[] descending = {
                MiningSpeed.Tier.NETHERITE, MiningSpeed.Tier.DIAMOND,
                MiningSpeed.Tier.IRON, MiningSpeed.Tier.STONE, MiningSpeed.Tier.WOODEN,
        };
        double previous = 0.0;
        for (MiningSpeed.Tier tier : descending) {
            double taken = seconds(tier, 0, 0, 0);
            assertTrue(taken >= previous, tier + " is not slower than the tier above it");
            previous = taken;
        }
    }

    @Test
    void lessEfficiencyIsSlowerAndMoreIsFaster() {
        double previous = 0.0;
        for (int efficiency = 20; efficiency >= 0; efficiency--) {
            double taken = seconds(MiningSpeed.Tier.NETHERITE, efficiency, 0, 0);
            assertTrue(taken >= previous,
                    "Efficiency " + efficiency + " should not be faster than " + (efficiency + 1));
            previous = taken;
        }
    }

    /**
     * The gate on the caves being endgame content. A mid-game pickaxe has to
     * work — refusing it outright would be worse — but it has to hurt, or the
     * supply numbers mean nothing.
     */
    @Test
    void aMidGamePickaxeIsSeveralTimesSlowerThanReference() {
        double ratio = seconds(MiningSpeed.Tier.NETHERITE, 5, 0, 0)
                / seconds(MiningSpeed.Tier.NETHERITE, 15, 0, 0);
        assertTrue(ratio > 4.0, "Efficiency 5 is barely slower than 15: " + ratio);
    }

    @Test
    void hasteHelpsAndFatigueHurts() {
        double plain = seconds(MiningSpeed.Tier.NETHERITE, 15, 0, 0);
        assertTrue(seconds(MiningSpeed.Tier.NETHERITE, 15, 1, 0) < plain, "Haste did nothing");
        assertTrue(seconds(MiningSpeed.Tier.NETHERITE, 15, 2, 0)
                < seconds(MiningSpeed.Tier.NETHERITE, 15, 1, 0), "Haste II is not better than I");
        assertTrue(seconds(MiningSpeed.Tier.NETHERITE, 15, 0, 1) > plain, "Fatigue did nothing");
    }

    /**
     * Nothing may drive a block below the floor. Haste is uncapped by design, so
     * the floor is the only thing standing between a beacon and instant mining —
     * which would collapse the supply ceiling the whole economy is built on.
     */
    @Test
    void noGearStackEverBeatsTheFloor() {
        for (int haste = 0; haste <= 20; haste++) {
            for (int efficiency = 0; efficiency <= 40; efficiency += 5) {
                double taken = seconds(MiningSpeed.Tier.GOLDEN, efficiency, haste, 0);
                assertTrue(Double.isFinite(taken), "non-finite time at eff " + efficiency);
                assertTrue(taken >= FLOOR - 1e-9,
                        "eff " + efficiency + " haste " + haste + " beat the floor: " + taken);
                assertTrue(taken <= MiningSpeed.MAX_SECONDS + 1e-9);
            }
        }
    }

    /**
     * The other end. A deep Fatigue stack drives the speed ratio toward zero, and
     * without the ceiling the channel would outlast the session.
     */
    @Test
    void noFatigueStackEverExceedsTheCeiling() {
        for (int fatigue = 0; fatigue <= 10; fatigue++) {
            double taken = seconds(MiningSpeed.Tier.WOODEN, 0, 0, fatigue);
            assertTrue(Double.isFinite(taken), "non-finite time at fatigue " + fatigue);
            assertTrue(taken <= MiningSpeed.MAX_SECONDS + 1e-9);
        }
    }

    /** A missing or nonsensical speed degrades to the ceiling, never to infinity. */
    @Test
    void degenerateInputsDegradeRatherThanBlowUp() {
        assertEquals(MiningSpeed.MAX_SECONDS, MiningSpeed.seconds(1.5, REF, 0.0, FLOOR), 1e-9);
        assertEquals(MiningSpeed.MAX_SECONDS, MiningSpeed.seconds(1.5, REF, -3.0, FLOOR), 1e-9);
        assertEquals(MiningSpeed.MAX_SECONDS, MiningSpeed.seconds(1.5, 0.0, REF, FLOOR), 1e-9);
        assertEquals(MiningSpeed.MAX_SECONDS,
                MiningSpeed.seconds(1.5, REF, Double.NaN, FLOOR), 1e-9);
        assertEquals(0.0, MiningSpeed.speed(null, 15, 0, 0), 1e-9);
    }

    /** A channel always advances, however short the computed time. */
    @Test
    void everyChannelIsAtLeastOneTick() {
        assertTrue(MiningSpeed.ticks(0.0) >= 1);
        assertTrue(MiningSpeed.ticks(0.001) >= 1);
        assertEquals(30, MiningSpeed.ticks(1.5));
        assertEquals(80, MiningSpeed.ticks(4.0));
    }
}
