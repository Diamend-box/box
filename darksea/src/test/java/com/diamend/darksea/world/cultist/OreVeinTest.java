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

    // ------------------------------------------------------------------
    // The geode rind
    // ------------------------------------------------------------------

    /**
     * The rind must completely surround the core. A gap would leave a crystal
     * block touching raw basalt, which is the difference between "a geode" and
     * "someone spilled ore in the wall".
     */
    @Test
    void theShellFullyEnclosesTheCoreAndNeverOverlapsIt() {
        for (long seed = 1; seed <= 25; seed++) {
            List<OreVein.Offset> core = OreVein.grow(seed, OreVein.sizeFor(seed, 60, 150));
            List<OreVein.Offset> shell = OreVein.shell(core);
            Set<OreVein.Offset> body = new HashSet<>(core);
            Set<OreVein.Offset> rind = new HashSet<>(shell);

            assertEquals(shell.size(), rind.size(), "seed " + seed + ": duplicate shell cells");
            for (OreVein.Offset cell : shell) {
                assertTrue(!body.contains(cell),
                        "seed " + seed + ": shell overlaps the core at " + cell);
            }
            Set<OreVein.Offset> all = new HashSet<>(body);
            all.addAll(rind);
            int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            for (OreVein.Offset block : core) {
                for (int[] face : faces) {
                    OreVein.Offset neighbour = new OreVein.Offset(
                            block.dx() + face[0], block.dy() + face[1], block.dz() + face[2]);
                    assertTrue(all.contains(neighbour),
                            "seed " + seed + ": core block " + block + " is exposed at " + neighbour);
                }
            }
        }
    }

    /** The shell is recomputed on every load rather than stored, so it must be stable. */
    @Test
    void theSameCoreAlwaysProducesTheSameShell() {
        for (long seed = 1; seed <= 10; seed++) {
            List<OreVein.Offset> core = OreVein.grow(seed, 90);
            assertEquals(OreVein.shell(core), OreVein.shell(OreVein.grow(seed, 90)),
                    "seed " + seed + ": shell is not reproducible");
        }
        assertTrue(OreVein.shell(List.of()).isEmpty());
    }

    /**
     * Buds are hashed from position rather than drawn from a stream, so a cell's
     * answer cannot depend on what order the shell happened to be walked in —
     * which is what makes recomputing the shell safe.
     */
    @Test
    void budsAreStablePerCellAndLandNearTheConfiguredRate() {
        List<OreVein.Offset> shell = OreVein.shell(OreVein.grow(7L, 120));
        for (OreVein.Offset cell : shell) {
            assertEquals(OreVein.isBud(7L, cell, 7), OreVein.isBud(7L, cell, 7),
                    "bud decision is not stable for " + cell);
        }
        long buds = shell.stream().filter(cell -> OreVein.isBud(7L, cell, 7)).count();
        double rate = (double) buds / shell.size();
        assertTrue(rate > 0.05 && rate < 0.30,
                "one-in-7 buds came out at " + rate + " of the rind");

        // Degenerate rates are answers, not crashes.
        assertTrue(OreVein.isBud(7L, shell.get(0), 1), "one-in-1 should make everything a bud");
        assertTrue(!OreVein.isBud(7L, shell.get(0), 0), "one-in-0 should make nothing a bud");
    }
}
