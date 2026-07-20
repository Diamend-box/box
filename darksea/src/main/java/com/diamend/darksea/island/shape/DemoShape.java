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

    /**
     * Relative pick weight within a tier's pool. The standard shapes all
     * sit at 10; a rare landmark class (the ruined castle) uses a small
     * weight so it turns up once or twice per sea, not once per ring.
     */
    default int rarityWeight() {
        return 10;
    }

    /** How many loot chests this shape hides at the given tier. */
    default int chestCount(int tier) {
        return tier >= 4 ? 3 : tier == 3 ? 2 : 1;
    }

    /**
     * How many of those chests are elected vaults (rolling the richer vault
     * loot table). Election itself stays deterministic in LootMath; islands
     * with fewer than two chests never have a vault regardless.
     */
    default int vaultChestCount() {
        return 1;
    }

    /**
     * Added to the island's ring tier when picking its mob pool: a boost of
     * 1 makes a ring-2 island fight with ring 3's roster. The spawner
     * falls back to the ring's own pool where no deeper pool exists.
     */
    default int mobTierBoost() {
        return 0;
    }

    /** Added to the per-island concurrent mob cap — big shapes hold more. */
    default int mobCapBonus() {
        return 0;
    }

    /**
     * Minimum Chronon wealth multiplier for islands of this shape (0 = no
     * floor, use the position roll as-is). A rich district drowns rich.
     */
    default double wealthFloor() {
        return 0.0;
    }

    /** Horizontal half-extent budget the placer must pre-load chunks for. */
    default int radiusBudget() {
        return 30;
    }

    ShapeBuild build(int tier, long seed);
}
