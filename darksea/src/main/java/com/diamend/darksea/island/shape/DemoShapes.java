package com.diamend.darksea.island.shape;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** The shape roster and the per-tier pick. */
public final class DemoShapes {

    public static final List<DemoShape> ALL = List.of(
            new RockySpire(),
            new TwinAtoll(),
            new RuinedWatchtower(),
            new SeaBeastBones(),
            new CorruptedForest(),
            new VolcanicCone(),
            new AbyssalMonolith(),
            new MariphageNest(),
            new RuinedCastle(),
            // Not in any ring's pool — its tier is above every generated ring,
            // so forTier can never return it. Listed here only so byId() can
            // find it when a soft reset re-pastes the one that was placed.
            new CultistLandfall());

    private DemoShapes() {
    }

    public static DemoShape byId(String id) {
        for (DemoShape shape : ALL) {
            if (shape.id().equals(id)) {
                return shape;
            }
        }
        return null;
    }

    public static List<DemoShape> forTier(int tier) {
        return ALL.stream().filter(shape -> shape.fitsTier(tier)).toList();
    }

    /** Weighted pick — rare landmark shapes carry small rarity weights. */
    public static DemoShape pick(int tier, Random rng) {
        return pick(tier, rng, Map.of());
    }

    /**
     * Weighted pick with config overrides: {@code overrides} maps a shape id
     * to the weight it should carry in this ring, and anything it does not
     * name keeps the shape's own built-in rarity.
     *
     * <p>Setting a shape to 0 removes it from the ring. Zeroing every shape a
     * ring can raise would leave nothing to pick, so that case falls back to
     * the built-in weights rather than throwing — a config that says "no
     * islands here" should be expressed by setting the ring's island count to
     * 0, and it would be a poor trade to take the server down over it.
     */
    public static DemoShape pick(int tier, Random rng, Map<String, Integer> overrides) {
        List<DemoShape> pool = forTier(tier);
        if (pool.isEmpty()) {
            throw new IllegalStateException("no demo shape fits tier " + tier);
        }
        int total = 0;
        for (DemoShape shape : pool) {
            total += weightOf(shape, tier, overrides);
        }
        if (total <= 0) {
            overrides = Map.of();
            for (DemoShape shape : pool) {
                total += shape.rarityWeight(tier);
            }
        }
        int roll = rng.nextInt(total);
        for (DemoShape shape : pool) {
            roll -= weightOf(shape, tier, overrides);
            if (roll < 0) {
                return shape;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static int weightOf(DemoShape shape, int tier, Map<String, Integer> overrides) {
        Integer override = overrides.get(shape.id());
        return override != null ? Math.max(0, override) : shape.rarityWeight(tier);
    }

    /**
     * Deterministic seed for an island at a world position, so a soft reset
     * rebuilds the very same rocks it built the first time.
     */
    public static long seedFor(int x, int z) {
        long seed = x * 341873128712L ^ z * 132897987541L;
        seed ^= seed >>> 29;
        return seed;
    }
}
