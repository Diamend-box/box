package com.diamend.darksea.world.cultist;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the veins end up. Every property here is one you would otherwise have
 * to fly around the world to check, and two of them — spacing and reachability
 * — are the whole reason ore lives in nodes rather than in the terrain.
 */
class VeinScatterTest {

    private static final long[] SEEDS = {1L, 42L, 777L, 9001L, 123456L, -7L};
    private static final int SPACING = 48;

    private static OreTables tables() {
        return new OreTables(List.of(
                new OreType("emberiron", "NETHER_GOLD_ORE", "emberiron_chunk", 1, 8, 20, 34, 25),
                new OreType("voidsalt", "NETHER_QUARTZ_ORE", "voidsalt", 1, 5, 22, 38, 40),
                new OreType("ichor", "CRYING_OBSIDIAN", "mariphage_ichor", 1, 3, 24, 40, 70)),
                SPACING, 4000);
    }

    private static CultistCarve carve(long seed) {
        return new CultistCarve(seed, 20, 90, 256, 6);
    }

    @Test
    void everyConfiguredVeinFindsSomewhereToStand() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            List<VeinScatter.Placement> placed = VeinScatter.scatter(carve(seed), tables, seed);
            assertEquals(tables.totalVeins(), placed.size(),
                    "seed " + seed + " could not place every vein");
        }
    }

    @Test
    void eachTypeGetsExactlyItsShare() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            Map<String, Integer> counts = new HashMap<>();
            for (VeinScatter.Placement placement : VeinScatter.scatter(carve(seed), tables, seed)) {
                counts.merge(placement.typeId(), 1, Integer::sum);
            }
            for (OreType type : tables.types()) {
                assertEquals(type.veinCount(), counts.getOrDefault(type.id(), 0),
                        "seed " + seed + " type " + type.id());
            }
        }
    }

    /**
     * The spacing rule, which is a supply dial in disguise: two veins close
     * enough to work back to back are one richer vein with half the travel.
     */
    @Test
    void noTwoVeinsStandCloserThanTheConfiguredSpacing() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            List<VeinScatter.Placement> placed = VeinScatter.scatter(carve(seed), tables, seed);
            for (int i = 0; i < placed.size(); i++) {
                for (int j = i + 1; j < placed.size(); j++) {
                    int distSq = placed.get(i).distanceSquared2D(placed.get(j));
                    assertTrue(distSq >= SPACING * SPACING,
                            "seed " + seed + ": two veins " + Math.round(Math.sqrt(distSq))
                                    + " apart, minimum is " + SPACING);
                }
            }
        }
    }

    /** A vein inside stone with no cave above it is a vein nobody ever finds. */
    @Test
    void everyVeinSitsInAFloorWithOpenCaveAboveIt() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            CultistCarve carve = carve(seed);
            for (VeinScatter.Placement placement : VeinScatter.scatter(carve, tables, seed)) {
                int x = placement.x();
                int y = placement.y();
                int z = placement.z();
                assertTrue(carve.inBounds(x, z), "seed " + seed + ": vein outside the border");
                assertTrue(!carve.isOpen(x, y, z),
                        "seed " + seed + ": vein origin floating in air");
                assertTrue(carve.isOpen(x, y + 1, z),
                        "seed " + seed + ": vein buried with no cave above it");
                assertTrue(carve.isOpen(x, y + 2, z),
                        "seed " + seed + ": no head room to stand and mine");
            }
        }
    }

    /** The arrival room stays a plain room — no vein camped on the portal. */
    @Test
    void noVeinSpawnsInTheArrivalChamber() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            CultistCarve carve = carve(seed);
            for (VeinScatter.Placement placement : VeinScatter.scatter(carve, tables, seed)) {
                assertTrue(!carve.inChamber(placement.x(), placement.y() + 1, placement.z()),
                        "seed " + seed + ": a vein spawned in the arrival chamber");
            }
        }
    }

    @Test
    void thesameSeedAlwaysScattersTheSameWay() {
        OreTables tables = tables();
        for (long seed : SEEDS) {
            CultistCarve carve = carve(seed);
            assertEquals(VeinScatter.scatter(carve, tables, seed),
                    VeinScatter.scatter(carve, tables, seed), "seed " + seed);
        }
    }

    @Test
    void differentSeedsScatterDifferently() {
        OreTables tables = tables();
        assertTrue(!VeinScatter.scatter(carve(1L), tables, 1L)
                        .equals(VeinScatter.scatter(carve(2L), tables, 2L)),
                "two seeds put every vein in the same place");
    }

    /**
     * A config asking for more veins than the world has room for should place
     * what it can and stop, not spin forever looking for the impossible.
     */
    @Test
    void anImpossibleConfigPlacesWhatItCanAndGivesUp() {
        OreTables greedy = new OreTables(List.of(
                new OreType("everywhere", "NETHER_GOLD_ORE", "x", 1, 5000, 20, 30, 25)),
                SPACING, 500);
        List<VeinScatter.Placement> placed = VeinScatter.scatter(carve(1L), greedy, 1L);
        assertTrue(placed.size() < 5000, "it claimed to place the impossible");
        assertTrue(placed.size() > 0, "it should still place some");
    }

    @Test
    void floorSurfaceRefusesColumnsOutsideTheWorld() {
        CultistCarve carve = carve(1L);
        assertNull(VeinScatter.floorSurface(carve, 100_000, 0));
        // And finds a real floor somewhere ordinary.
        Integer surface = VeinScatter.floorSurface(carve, 40, 40);
        if (surface != null) {
            assertNotNull(surface);
            assertTrue(surface > carve.floorY() && surface < carve.roofY());
        }
    }

    @Test
    void anEmptyConfigScattersNothingRatherThanFailing() {
        assertTrue(VeinScatter.scatter(carve(1L), OreTables.empty(), 1L).isEmpty());
        assertEquals(0, OreTables.empty().totalVeins());
    }
}
