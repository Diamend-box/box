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
            new VolcanicCone(),
            new AbyssalMonolith());

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

    public static DemoShape pick(int tier, Random rng) {
        List<DemoShape> pool = forTier(tier);
        if (pool.isEmpty()) {
            throw new IllegalStateException("no demo shape fits tier " + tier);
        }
        return pool.get(rng.nextInt(pool.size()));
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
