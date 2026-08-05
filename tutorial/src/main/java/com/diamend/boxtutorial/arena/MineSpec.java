package com.diamend.boxtutorial.arena;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * One regenerating mine, the way a mine-reset plugin describes one: a cuboid
 * and a table of what fills it.
 *
 * <p>Offsets are relative to the instance origin, which is the middle of the
 * platform at floor level — so {@code y: 1} is the first block a player can
 * stand on.
 */
public class MineSpec {

    /** A block position relative to an instance's origin. */
    public record Offset(int x, int y, int z) {
    }

    private final String id;
    private final String name;
    private final Offset min;
    private final Offset max;
    private final int refillAtPercent;
    private final Map<Material, Integer> blocks;
    private final int totalWeight;

    public MineSpec(String id, String name, Offset min, Offset max, int refillAtPercent,
                    Map<Material, Integer> blocks) {
        this.id = id;
        this.name = name;
        // Normalise the corners so a config that writes them the other way
        // round still describes the same box.
        this.min = new Offset(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()),
                Math.min(min.z(), max.z()));
        this.max = new Offset(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()),
                Math.max(min.z(), max.z()));
        this.refillAtPercent = Math.max(1, Math.min(100, refillAtPercent));
        this.blocks = new LinkedHashMap<>(blocks);
        int weight = 0;
        for (int value : this.blocks.values()) {
            weight += Math.max(0, value);
        }
        this.totalWeight = weight;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Offset min() {
        return min;
    }

    public Offset max() {
        return max;
    }

    public Map<Material, Integer> blocks() {
        return Map.copyOf(blocks);
    }

    /** How many blocks the mine holds when it's full. */
    public int volume() {
        return (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }

    /**
     * How many blocks have to be taken out before it refills.
     *
     * <p>A mine that only refills when it is completely empty leaves the last
     * player scraping the corners, so the default refills it at 40% left.
     */
    public int minedBeforeRefill() {
        return Math.max(1, (int) Math.ceil(volume() * (100 - refillAtPercent) / 100.0));
    }

    public int refillAtPercent() {
        return refillAtPercent;
    }

    /** True when this offset is inside the mine — i.e. a block players may break. */
    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x()
                && y >= min.y() && y <= max.y()
                && z >= min.z() && z <= max.z();
    }

    /** Picks a block for one slot in the mine, by weight. */
    public Material roll(Random random) {
        if (blocks.isEmpty() || totalWeight <= 0) {
            return Material.STONE;
        }
        int roll = random.nextInt(totalWeight);
        for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
            roll -= Math.max(0, entry.getValue());
            if (roll < 0) {
                return entry.getKey();
            }
        }
        return blocks.keySet().iterator().next();
    }

    /** True when this mine can produce that material — used to check a step. */
    public boolean produces(Material material) {
        return blocks.containsKey(material);
    }
}
