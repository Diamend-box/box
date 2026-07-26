package com.diamend.darksea.world.cultist;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cave shape, proven without a server. Two properties matter and both are
 * the kind that only show up in aggregate: the caves must be open enough to be
 * caves rather than a solid block, and — the one that actually bites — almost
 * all of that open space must be reachable on foot from where the portal drops
 * you.
 */
class CultistCarveTest {

    private static final long[] SEEDS = {1L, 42L, 777L, 9001L, 123456L, -50L};

    private static final int FLOOR = 20;
    private static final int ROOF = 90;
    private static final int HALF_EXTENT = 256;
    private static final int CHAMBER = 6;

    private static CultistCarve carve(long seed) {
        return new CultistCarve(seed, FLOOR, ROOF, HALF_EXTENT, CHAMBER);
    }

    @Test
    void theWorldIsASealedBox() {
        CultistCarve carve = carve(1L);
        assertTrue(carve.inBounds(0, 0));
        assertTrue(carve.inBounds(HALF_EXTENT, -HALF_EXTENT), "the corner is still inside");
        assertFalse(carve.inBounds(HALF_EXTENT + 1, 0), "past the wall is outside");

        // Nothing outside the bound or the vertical band is ever open, so the
        // generator can fill those solid without asking twice.
        assertFalse(carve.isOpen(HALF_EXTENT + 1, 50, 0), "no air past the border");
        assertFalse(carve.isOpen(0, FLOOR, 0), "the floor itself is solid");
        assertFalse(carve.isOpen(0, FLOOR - 5, 0), "nothing below the floor");
        assertFalse(carve.isOpen(0, ROOF, 0), "the roof itself is solid");
        assertFalse(carve.isOpen(0, ROOF + 5, 0), "no sky above the roof");
    }

    /** The portal must never drop anyone inside stone. */
    @Test
    void theArrivalChamberIsAlwaysOpen() {
        for (long seed : SEEDS) {
            CultistCarve carve = carve(seed);
            for (int y = FLOOR + 1; y <= FLOOR + CHAMBER; y++) {
                assertTrue(carve.isOpen(0, y, 0), "seed " + seed + ": chamber sealed at y=" + y);
            }
            assertTrue(carve.isOpen(CHAMBER - 1, FLOOR + 1, 0), "seed " + seed);
        }
    }

    @Test
    void thesameSeedAlwaysCarvesTheSameCaves() {
        CultistCarve first = carve(42L);
        CultistCarve second = carve(42L);
        for (int x = -60; x <= 60; x += 7) {
            for (int z = -60; z <= 60; z += 7) {
                for (int y = FLOOR + 1; y < ROOF; y += 5) {
                    assertEquals(first.isOpen(x, y, z), second.isOpen(x, y, z),
                            "diverged at " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void differentSeedsCarveDifferentCaves() {
        CultistCarve a = carve(1L);
        CultistCarve b = carve(2L);
        int differences = 0;
        for (int x = -60; x <= 60; x += 3) {
            for (int z = -60; z <= 60; z += 3) {
                if (a.isOpen(x, 50, z) != b.isOpen(x, 50, z)) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 100, "seeds barely differ: " + differences);
    }

    /** Not a solid block, not a hollow shell. */
    @Test
    void opennessStaysInABand() {
        for (long seed : SEEDS) {
            double open = carve(seed).openFraction(-160, -160, 160, 160, 7);
            assertTrue(open > 0.08, "seed " + seed + " is nearly solid: " + open);
            assertTrue(open < 0.40, "seed " + seed + " is a hollow shell: " + open);
        }
    }

    /**
     * The one that guards the cliff. Openness alone says nothing about whether
     * the caves are one network or a thousand sealed pockets — at a slightly
     * tighter tunnel threshold this same 21% open measures under 10% reachable,
     * which would be a world you spawn into and cannot leave.
     */
    @Test
    void almostEverythingIsReachableFromTheChamber() {
        for (long seed : SEEDS) {
            double reachable = reachableFraction(carve(seed));
            assertTrue(reachable > 0.90,
                    "seed " + seed + ": only " + Math.round(reachable * 100)
                            + "% of the caves reachable from the arrival chamber");
        }
    }

    /**
     * Flood fill over a sampled lattice from the arrival chamber. Sampling at a
     * stride rather than every block keeps this fast; a stride that finds two
     * regions connected can only overstate connectivity if the real blocks
     * between them are open too, which they are — a stride-2 step is inside one
     * passage width.
     */
    private static double reachableFraction(CultistCarve carve) {
        final int step = 2;
        final int radius = 120;
        Set<Long> open = new HashSet<>();
        for (int x = -radius; x <= radius; x += step) {
            for (int z = -radius; z <= radius; z += step) {
                for (int y = FLOOR + 2; y < ROOF - 2; y += step) {
                    if (carve.isOpen(x, y, z)) {
                        open.add(key(x, y, z));
                    }
                }
            }
        }
        long start = key(0, FLOOR + 2, 0);
        assertTrue(open.contains(start), "the chamber is not on the sampled lattice");

        Deque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new int[]{0, FLOOR + 2, 0});
        seen.add(start);
        int[][] dirs = {{step, 0, 0}, {-step, 0, 0}, {0, step, 0},
                {0, -step, 0}, {0, 0, step}, {0, 0, -step}};
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            for (int[] dir : dirs) {
                int nx = current[0] + dir[0];
                int ny = current[1] + dir[1];
                int nz = current[2] + dir[2];
                long k = key(nx, ny, nz);
                if (open.contains(k) && seen.add(k)) {
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return (double) seen.size() / open.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
    }
}
