package com.diamend.darksea.world.cultist;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vein shape. The invariant that matters is contiguity — a vein that comes out
 * as two halves with rock between them is a vein a player can only half find.
 */
class OreVeinTest {

    private static final long[] SEEDS = {1L, 2L, 3L, 42L, 777L, 9001L, -13L};

    @Test
    void veinsAreExactlyTheSizeAskedFor() {
        for (long seed : SEEDS) {
            for (int size : new int[]{1, 5, 20, 30, 40, 64}) {
                List<OreVein.Offset> vein = OreVein.grow(seed, size);
                assertEquals(size, vein.size(), "seed " + seed + " size " + size);
            }
        }
    }

    @Test
    void veinsAreAlwaysOneConnectedMass() {
        for (long seed : SEEDS) {
            for (int size : new int[]{1, 8, 25, 40, 64}) {
                List<OreVein.Offset> vein = OreVein.grow(seed, size);
                assertTrue(OreVein.isContiguous(vein),
                        "seed " + seed + " size " + size + " grew in pieces");
            }
        }
    }

    @Test
    void aVeinAlwaysContainsItsOwnOrigin() {
        for (long seed : SEEDS) {
            assertTrue(OreVein.grow(seed, 30).contains(new OreVein.Offset(0, 0, 0)),
                    "seed " + seed);
        }
    }

    @Test
    void noBlockIsPlacedTwice() {
        for (long seed : SEEDS) {
            List<OreVein.Offset> vein = OreVein.grow(seed, 40);
            assertEquals(vein.size(), new HashSet<>(vein).size(), "seed " + seed);
        }
    }

    @Test
    void theSameSeedGrowsTheSameVein() {
        for (long seed : SEEDS) {
            assertEquals(OreVein.grow(seed, 30), OreVein.grow(seed, 30), "seed " + seed);
        }
    }

    @Test
    void differentSeedsGrowDifferentVeins() {
        Set<List<OreVein.Offset>> shapes = new HashSet<>();
        for (long seed : SEEDS) {
            shapes.add(OreVein.grow(seed, 30));
        }
        assertTrue(shapes.size() > SEEDS.length / 2, "veins are too samey: " + shapes.size());
    }

    /**
     * A vein should be a lump you stand in front of, not a worm boring across
     * the map. Thirty blocks spread over a reach of thirty would be a line.
     */
    @Test
    void veinsStayCompactRatherThanSnaking() {
        for (long seed : SEEDS) {
            List<OreVein.Offset> vein = OreVein.grow(seed, 40);
            int reach = 0;
            for (OreVein.Offset offset : vein) {
                reach = Math.max(reach, offset.manhattan());
            }
            assertTrue(reach <= vein.size() / 2,
                    "seed " + seed + ": 40 blocks reaching " + reach + " is a worm, not a vein");
        }
    }

    /**
     * Veins sprawl along the floor rather than boring up through the roof.
     *
     * <p>This one guards the ordering of the neighbour table, which is easy to
     * "tidy" into x, y, z and thereby invert the bias — the walk weights steps
     * by index, so putting ±y at index 2 makes veins grow tall and thin and
     * starves the z axis entirely. That mistake leaves every other property
     * here passing.
     */
    @Test
    void veinsSpreadWiderThanTheyAreTall() {
        int horizontal = 0;
        int vertical = 0;
        for (long seed : SEEDS) {
            for (OreVein.Offset offset : OreVein.grow(seed, 40)) {
                horizontal += Math.abs(offset.dx()) + Math.abs(offset.dz());
                vertical += Math.abs(offset.dy());
            }
        }
        assertTrue(horizontal > vertical * 2,
                "veins climb too much: " + horizontal + " horizontal vs " + vertical + " vertical");
    }

    @Test
    void sizesStayInTheConfiguredBandAndVary() {
        Set<Integer> seen = new HashSet<>();
        for (long seed = 0; seed < 40; seed++) {
            int size = OreVein.sizeFor(seed, 20, 40);
            assertTrue(size >= 20 && size <= 40, "seed " + seed + " gave " + size);
            seen.add(size);
        }
        assertTrue(seen.size() > 5, "every vein is the same size: " + seen);
        // A degenerate band is legal and returns the only value it can.
        assertEquals(7, OreVein.sizeFor(123L, 7, 7));
        // Reversed bounds are tolerated rather than throwing at a config typo.
        assertTrue(OreVein.sizeFor(123L, 40, 20) >= 20);
    }
}
