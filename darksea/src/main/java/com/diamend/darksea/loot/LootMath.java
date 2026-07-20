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
        int[] indices = vaultChestIndices(x, z, chestCount, 1);
        return indices.length == 0 ? -1 : indices[0];
    }

    /**
     * Deterministic election of up to {@code vaults} distinct vault chests
     * among {@code chestCount} — the landmark islands hide more than one.
     * At least one chest always stays plain, and islands with fewer than
     * two chests elect none. Sorted ascending; stable in the origin.
     */
    public static int[] vaultChestIndices(int x, int z, int chestCount, int vaults) {
        if (chestCount < 2 || vaults < 1) {
            return new int[0];
        }
        int count = Math.min(vaults, chestCount - 1);
        int[] indices = new int[chestCount];
        for (int i = 0; i < chestCount; i++) {
            indices[i] = i;
        }
        // Partial Fisher-Yates seeded by the origin: java.util.Random's
        // algorithm is pinned by its spec, so the election never drifts.
        java.util.Random rng = new java.util.Random(mix(x, z));
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(chestCount - i);
            int swap = indices[i];
            indices[i] = indices[j];
            indices[j] = swap;
        }
        int[] picked = java.util.Arrays.copyOf(indices, count);
        java.util.Arrays.sort(picked);
        return picked;
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
