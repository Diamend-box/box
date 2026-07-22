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

    /**
     * Per-tier pick weight. Defaults to the flat {@link #rarityWeight()}, but a
     * shape can weigh differently by ring — the Mariphage nest is a rare stray
     * in the Abyssal Reaches yet the norm out in the Sunless Trench.
     */
    default int rarityWeight(int tier) {
        return rarityWeight();
    }

    /**
     * A named mob this shape always keeps standing — its resident boss. The
     * spawner re-raises it whenever the island is empty of one, past the
     * ordinary mob cap. Null (the default) means no guaranteed occupant.
     */
    default String bossMob() {
        return null;
    }

    /** Vanilla fallback entity for {@link #bossMob()} where MythicMobs is absent. */
    default String bossFallback() {
        return null;
    }

    /**
     * The chance (0..1) that a given island of this shape, at this tier,
     * actually keeps its {@link #bossMob()} standing. A Mariphage nest always
     * does (1.0). A shape that only <em>sometimes</em> fell to the Order — the
     * outer Naxome the plague reached first, out in the Trench — returns a
     * small chance at those tiers and 0 elsewhere. The roll is taken once per
     * island from its position (see {@code IslandInstance}), so a soft reset
     * re-decides identically. Zero whenever there is no {@link #bossMob()}.
     */
    default double residentBossChance(int tier) {
        return bossMob() != null ? 1.0 : 0.0;
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
     * Per-tier concurrent-cap bonus. Defaults to the flat {@link #mobCapBonus()}
     * at every tier, but a shape can garrison harder in a particular ring — a
     * fallen Naxome outpost out in the Sunless Trench crawls with the plague.
     */
    default int mobCapBonus(int tier) {
        return mobCapBonus();
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

    /**
     * A custom DarkSeaItems id this shape seeds into its own chests on top of
     * the tier loot table, or null (the default) for none. The Mariphage nest
     * seeds the hunter's Soulwake Compass — kept as a plain id string so this
     * package stays free of the Bukkit item registry.
     */
    default String chestBonusItem() {
        return null;
    }

    /**
     * Per-chest chance (0..1) that {@link #chestBonusItem()} turns up when a
     * chest of this island refills — vault chests roll the richer odds. Zero
     * whenever the shape seeds nothing.
     */
    default double chestBonusChance(boolean vault) {
        return 0.0;
    }

    ShapeBuild build(int tier, long seed);
}
