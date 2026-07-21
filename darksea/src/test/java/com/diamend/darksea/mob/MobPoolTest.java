package com.diamend.darksea.mob;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-JVM checks for the minimum-tier ("bleed-down") spawn pools. */
class MobPoolTest {

    private static final MobPool.MobEntry SAILOR = new MobPool.MobEntry("CrazedSailor", "HUSK", 10, 1);
    private static final MobPool.MobEntry ACOLYTE = new MobPool.MobEntry("VironicAcolyte", "VINDICATOR", 5, 3);
    private static final MobPool.MobEntry LORD = new MobPool.MobEntry("VironicLord", "EVOKER", 6, 10);

    private static int weightOf(List<MobPool.Slot> pool, String type) {
        for (MobPool.Slot slot : pool) {
            if (slot.entry().type().equals(type)) {
                return slot.weight();
            }
        }
        return -1;
    }

    @Test
    void poolsCombineLowerTiersWithDecay() {
        Map<Integer, List<MobPool.Slot>> pools = MobPool.build(
                Map.of(1, List.of(SAILOR), 2, List.of(ACOLYTE), 4, List.of(LORD)), 0.5);

        // Tier 1 sees only its own natives, at full (scaled) weight.
        assertEquals(1, pools.get(1).size());
        assertEquals(10_000, weightOf(pools.get(1), "CrazedSailor"));

        // Tier 4 sees everything at or below it, halved per ring of distance
        // (the gap at tier 3 doesn't break anything).
        List<MobPool.Slot> deep = pools.get(4);
        assertEquals(3, deep.size());
        assertEquals(6_000, weightOf(deep, "VironicLord"));            // gap 0
        assertEquals(1_250, weightOf(deep, "VironicAcolyte"));         // 5 × 0.25
        assertEquals(1_250, weightOf(deep, "CrazedSailor"));           // 10 × 0.125
    }

    @Test
    void zeroDecayRestoresStrictPerTierSets() {
        Map<Integer, List<MobPool.Slot>> pools = MobPool.build(
                Map.of(1, List.of(SAILOR), 4, List.of(LORD)), 0.0);

        assertEquals(List.of("VironicLord"),
                pools.get(4).stream().map(slot -> slot.entry().type()).toList());
        // Tiers with no natives and no bleed get no pool at all.
        assertFalse(pools.containsKey(2));
        assertFalse(pools.containsKey(3));
    }

    @Test
    void undeclaredHigherTiersStillInheritLowerMobs() {
        // Only tier 1 declared: tiers 2–4 still get (decayed) sailors, so a
        // half-filled mobs.yml never leaves deep islands empty.
        Map<Integer, List<MobPool.Slot>> pools = MobPool.build(Map.of(1, List.of(SAILOR)), 0.35);

        for (int tier = 1; tier <= 4; tier++) {
            assertNotNull(pools.get(tier), "tier " + tier + " missing");
        }
        assertEquals(429, weightOf(pools.get(4), "CrazedSailor"));     // 10 × 0.35³ × 1000
    }

    @Test
    void theTrenchInheritsTheReachesFoesThinned() {
        // The roster names foes only through tier 4, but the Sunless Trench
        // (tier 5) must still roam: the builder carries tier 4 out one ring,
        // decayed, so the Trench sees the same Abomination/Lord as tier 4 but
        // sparser — never an empty ring.
        MobPool.MobEntry abom = new MobPool.MobEntry("NaxianAbomination", "RAVAGER", 8, 10);
        Map<Integer, List<MobPool.Slot>> pools = MobPool.build(
                Map.of(4, List.of(abom, LORD)), 0.35);

        List<MobPool.Slot> trench = pools.get(5);
        assertNotNull(trench, "the Trench (tier 5) must inherit a roaming pool");
        assertEquals(2_800, weightOf(trench, "NaxianAbomination"));  // 8 × 0.35
        assertEquals(2_100, weightOf(trench, "VironicLord"));        // 6 × 0.35
        // Thinner than the Reaches, where the same foes roam at full weight.
        assertTrue(weightOf(pools.get(4), "NaxianAbomination")
                        > weightOf(trench, "NaxianAbomination"),
                "the Trench must roam sparser than the Reaches");
    }

    @Test
    void weightsThatDecayToZeroAreDropped() {
        Map<Integer, List<MobPool.Slot>> pools = MobPool.build(
                Map.of(1, List.of(new MobPool.MobEntry("Whisper", null, 1, 1)), 3, List.of(ACOLYTE)),
                0.01);

        // 1 × 0.01² × 1000 = 0.1 → rounds to zero → gone from tier 3.
        assertEquals(-1, weightOf(pools.get(3), "Whisper"));
        assertEquals(5_000, weightOf(pools.get(3), "VironicAcolyte"));
    }

    @Test
    void pickHonoursWeightsRoughly() {
        List<MobPool.Slot> pool = List.of(
                new MobPool.Slot(SAILOR, 9_000), new MobPool.Slot(LORD, 1_000));
        Random rng = new Random(42);

        int sailors = 0;
        for (int i = 0; i < 10_000; i++) {
            if (MobPool.pick(pool, rng) == SAILOR) {
                sailors++;
            }
        }
        assertTrue(sailors > 8_500 && sailors < 9_500,
                "expected ~90% sailors, got " + sailors + "/10000");
    }

    @Test
    void vanillaNameFallsBackToType() {
        assertEquals("HUSK", SAILOR.vanillaName());
        assertEquals("Whisper", new MobPool.MobEntry("Whisper", null, 1, 1).vanillaName());
        assertEquals("Whisper", new MobPool.MobEntry("Whisper", "  ", 1, 1).vanillaName());
    }
}
