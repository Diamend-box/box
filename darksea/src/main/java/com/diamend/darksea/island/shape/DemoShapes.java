package com.diamend.darksea.island.shape;

import java.util.List;
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
        List<DemoShape> pool = forTier(tier);
        if (pool.isEmpty()) {
            throw new IllegalStateException("no demo shape fits tier " + tier);
        }
        int total = 0;
        for (DemoShape shape : pool) {
            total += shape.rarityWeight(tier);
        }
        int roll = rng.nextInt(total);
        for (DemoShape shape : pool) {
            roll -= shape.rarityWeight(tier);
            if (roll < 0) {
                return shape;
            }
        }
        return pool.get(pool.size() - 1);
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
