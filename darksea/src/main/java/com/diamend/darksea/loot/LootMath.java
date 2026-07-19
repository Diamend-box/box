package com.diamend.darksea.loot;

/**
 * Pure per-island loot math, keyed on the island's origin so the answers
 * survive restarts and soft resets without being stored anywhere.
 */
public final class LootMath {

    /** Island wealth spans poor wrecks to fat merchant caches. */
    public static final double WEALTH_MIN = 0.6;
    public static final double WEALTH_MAX = 1.8;

    private LootMath() {
    }

    /**
     * The island's Chronon wealth multiplier in [{@link #WEALTH_MIN},
     * {@link #WEALTH_MAX}): some islands were rich once. Deterministic in
     * the origin, uniform-ish across islands.
     */
    public static double wealthMultiplier(int x, int z) {
        return WEALTH_MIN + (WEALTH_MAX - WEALTH_MIN) * unit(mix(x, z));
    }

    /**
     * Deterministic index pick for the island's vault chest among its
     * {@code chestCount} chests, or -1 when the island has fewer than two
     * chests (a lone chest is never a vault).
     */
    public static int vaultChestIndex(int x, int z, int chestCount) {
        if (chestCount < 2) {
            return -1;
        }
        return (int) Math.floorMod(mix(x, z) >>> 17, chestCount);
    }

    /** Same avalanche mix the demo shapes use for their per-island seeds. */
    private static long mix(int x, int z) {
        long seed = x * 341873128712L ^ z * 132897987541L;
        seed ^= seed >>> 29;
        seed *= 0xBF58476D1CE4E5B9L;
        seed ^= seed >>> 32;
        return seed;
    }

    private static double unit(long seed) {
        return (seed >>> 11) / (double) (1L << 53);
    }
}
