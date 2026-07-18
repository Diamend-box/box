package com.diamend.darksea.island.shape;

import java.util.List;
import java.util.Map;

/**
 * The finished output of a shape: every block to set (relative to the island
 * origin, y 0 == sea level), where the loot chests go, and where mobs rise.
 * Chest positions are declared, not drawn — the placer's finalize step turns
 * them into real chests, exactly like a schematic's marker blocks. Tiers 1-2
 * hide one chest, tier 3 two, tier 4 three.
 */
public record ShapeBuild(Map<Rel, String> blocks, List<Rel> chests, List<Rel> mobSpawns,
                         Rel min, Rel max) {

    static ShapeBuild of(ShapeSketch sketch, List<Rel> chests, List<Rel> mobSpawns) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Rel pos : sketch.blocks().keySet()) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        return new ShapeBuild(Map.copyOf(sketch.blocks()), List.copyOf(chests),
                List.copyOf(mobSpawns), new Rel(minX, minY, minZ), new Rel(maxX, maxY, maxZ));
    }

    /** The main chest — the one the cutaway view and loot table key off. */
    public Rel chest() {
        return chests.get(0);
    }

    /** Horizontal half-extent — what the placer must pre-load chunks for. */
    public int radius() {
        return Math.max(Math.max(Math.abs(min.x()), Math.abs(max.x())),
                Math.max(Math.abs(min.z()), Math.abs(max.z())));
    }
}
