package com.diamend.darksea.world.cultist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The shape of one ore vein: a contiguous blob of block offsets around an
 * origin. Pure and deterministic — the same seed always grows the same blob —
 * so the shape can be asserted without a server.
 *
 * <p>Veins are deliberately <em>few and large</em>. A blob of twenty to forty
 * blocks takes real time to clear, which means whoever is working it is
 * stationary and exposed for that whole time, and everyone on the server knows
 * where it is. That is what makes a vein contested ground rather than a chore.
 * Scattering the same number of ore blocks as singles would produce the same
 * total supply and none of the tension.
 *
 * <p>Grown by random walk from the origin rather than carved from noise: a walk
 * is contiguous by construction, so there is no way for a vein to come out as
 * two disconnected halves with a wall between them.
 */
public final class OreVein {

    /** A block offset from the vein's origin. */
    public record Offset(int dx, int dy, int dz) {

        public int manhattan() {
            return Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        }
    }

    /**
     * Face neighbours, <strong>horizontals first</strong>. The order is load
     * bearing: {@link #pickDirection} weights by index, so the four horizontal
     * steps must occupy 0-3 and the two vertical ones 4-5. Listing them in the
     * natural x, y, z order instead puts ±y at index 2 and quietly inverts the
     * bias — veins grow tall and thin, and the z axis is starved.
     */
    private static final int[][] NEIGHBOURS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0},
    };

    private OreVein() {
    }

    /**
     * Grows a blob of exactly {@code size} offsets, always including the origin.
     *
     * <p>The walk keeps a frontier of placed blocks and repeatedly extends from
     * a random one, which gives a lumpy, organic mass rather than the long
     * worm a single-headed walk would trace. Vertical steps are drawn slightly
     * less often, so veins sprawl along the cave floor instead of boring
     * straight up through the roof.
     */
    public static List<Offset> grow(long seed, int size) {
        int wanted = Math.max(1, size);
        Random rng = new Random(seed);
        Set<Offset> placed = new LinkedHashSet<>();
        List<Offset> frontier = new ArrayList<>();

        Offset origin = new Offset(0, 0, 0);
        placed.add(origin);
        frontier.add(origin);

        // Bounded so a pathological seed can't spin: each attempt either places
        // a block or picks an already-filled neighbour, and we allow generous
        // slack for the latter.
        int attempts = 0;
        int maxAttempts = wanted * 64;
        while (placed.size() < wanted && attempts < maxAttempts) {
            attempts++;
            Offset from = frontier.get(rng.nextInt(frontier.size()));
            int[] step = NEIGHBOURS[pickDirection(rng)];
            Offset next = new Offset(from.dx() + step[0], from.dy() + step[1], from.dz() + step[2]);
            if (placed.add(next)) {
                frontier.add(next);
            }
        }
        return List.copyOf(placed);
    }

    /**
     * Picks a step direction, biased away from vertical. Indices 0-3 are the
     * four horizontal steps and 4-5 are up and down; drawing from a weighted
     * roll makes a vein about twice as likely to spread sideways as to climb.
     */
    private static int pickDirection(Random rng) {
        int roll = rng.nextInt(10);
        if (roll < 4) {
            return roll;            // east/west/north/south, weight 1 each
        }
        if (roll < 8) {
            return roll - 4;        // the horizontals again: double weight
        }
        return roll - 8 + 4;        // up or down
    }

    /**
     * A vein's size for a given seed, drawn from an inclusive band. Kept here
     * rather than at the call site so "how big is a vein" is one decision with
     * one test.
     */
    public static int sizeFor(long seed, int min, int max) {
        int low = Math.max(1, Math.min(min, max));
        int high = Math.max(low, Math.max(min, max));
        if (low == high) {
            return low;
        }
        // A different derived seed from grow()'s, so size and shape vary
        // independently rather than moving together.
        return low + new Random(seed ^ 0x5A1E_2B77L).nextInt(high - low + 1);
    }

    /** Whether every offset touches another face-to-face — the invariant a walk guarantees. */
    public static boolean isContiguous(List<Offset> offsets) {
        if (offsets.isEmpty()) {
            return true;
        }
        Set<Offset> remaining = new LinkedHashSet<>(offsets);
        List<Offset> queue = new ArrayList<>();
        Offset start = offsets.get(0);
        queue.add(start);
        remaining.remove(start);
        while (!queue.isEmpty()) {
            Offset current = queue.remove(queue.size() - 1);
            for (int[] step : NEIGHBOURS) {
                Offset neighbour = new Offset(
                        current.dx() + step[0], current.dy() + step[1], current.dz() + step[2]);
                if (remaining.remove(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return remaining.isEmpty();
    }
}
