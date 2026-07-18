package com.diamend.darksea.island.shape;

/**
 * A built-in island design. Implementations are pure functions of
 * {@code (tier, seed)} — the same inputs always rebuild the identical island,
 * which is what lets {@code /ds reset soft} heal a shaped island in place.
 */
public interface DemoShape {

    /** Stable id, also persisted in the island registry (e.g. "rocky-spire"). */
    String id();

    int minTier();

    int maxTier();

    default boolean fitsTier(int tier) {
        return tier >= minTier() && tier <= maxTier();
    }

    ShapeBuild build(int tier, long seed);
}
