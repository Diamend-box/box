package com.diamend.darksea.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Weighted spawn pools, one per island tier. A mob's configured tier is the
 * MINIMUM ring it appears in: each pool holds its own tier's entries at full
 * weight plus every lower tier's entries decayed by {@code decay^(ring gap)},
 * so early-ring mobs still wander the deep rings — just rarely. A decay of 0
 * restores strict per-tier sets.
 *
 * Deliberately Bukkit-free so plain-JVM tests can verify the math.
 */
public final class MobPool {

    /**
     * One mobs.yml line. {@code type} is tried as a MythicMobs internal name;
     * {@code fallback} (optional) is the vanilla entity spawned when the
     * Mythic mob is unavailable — when null or blank, {@code type} itself is
     * read as a vanilla entity name.
     */
    public record MobEntry(String type, String fallback, int weight, int level) {

        public String vanillaName() {
            return fallback != null && !fallback.isBlank() ? fallback : type;
        }
    }

    /** A pool slot: an entry plus its decayed weight, scaled ×1000 for integer math. */
    public record Slot(MobEntry entry, int weight) {
    }

    public static final double DEFAULT_DECAY = 0.35;

    /** The sea has four rings; pools are always built at least this far out. */
    private static final int MAX_TIER = 4;

    private static final int WEIGHT_SCALE = 1000;

    private MobPool() {
    }

    /**
     * Builds one combined pool per tier from 1 up to the highest declared
     * tier (at least {@value MAX_TIER}). Entries whose decayed weight rounds
     * to zero are dropped; tiers whose pool would be empty get no key at all.
     */
    public static Map<Integer, List<Slot>> build(Map<Integer, List<MobEntry>> sets, double decay) {
        if (sets.isEmpty()) {
            return Map.of();
        }
        double clamped = Math.min(1.0, Math.max(0.0, decay));
        int highest = MAX_TIER;
        for (int declared : sets.keySet()) {
            highest = Math.max(highest, declared);
        }
        Map<Integer, List<Slot>> pools = new HashMap<>();
        for (int tier = 1; tier <= highest; tier++) {
            List<Slot> pool = new ArrayList<>();
            for (Map.Entry<Integer, List<MobEntry>> source : sets.entrySet()) {
                int gap = tier - source.getKey();
                if (gap < 0) {
                    continue;
                }
                double factor = Math.pow(clamped, gap);
                for (MobEntry entry : source.getValue()) {
                    int weight = (int) Math.round(entry.weight() * (double) WEIGHT_SCALE * factor);
                    if (weight > 0) {
                        pool.add(new Slot(entry, weight));
                    }
                }
            }
            if (!pool.isEmpty()) {
                pools.put(tier, List.copyOf(pool));
            }
        }
        return Map.copyOf(pools);
    }

    public static MobEntry pick(List<Slot> pool, Random rng) {
        int total = 0;
        for (Slot slot : pool) {
            total += slot.weight();
        }
        int roll = rng.nextInt(total);
        for (Slot slot : pool) {
            roll -= slot.weight();
            if (roll < 0) {
                return slot.entry();
            }
        }
        return pool.get(pool.size() - 1).entry();
    }
}
