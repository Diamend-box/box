package com.diamend.robobear.mine;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * One mine, flattened to a plain axis-aligned box.
 *
 * <p>Deliberately dumb and immutable. Whatever the source plugin models a mine
 * as — with its composition, reset timers and schedules — RoboBear only ever
 * needs "is this block inside it", and the hot path
 * ({@code BlockBreakEvent}) must never reach back into that plugin to ask.
 * The box is snapshotted once and compared with six int comparisons.
 *
 * @param id          the mine's identifier, as the source plugin knows it
 * @param displayName what players should see, which may carry colour codes
 * @param world       world name; compared by name so an unloaded world is
 *                    simply never matched rather than throwing
 */
public record MineRegion(
        String id,
        String displayName,
        String world,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ) {

    /** Builds a region from two arbitrary corners, normalising the bounds. */
    public static MineRegion between(String id, String displayName, String world,
                                     int x1, int y1, int z1, int x2, int y2, int z2) {
        return new MineRegion(id, displayName, world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(String worldName, int x, int y, int z) {
        return world.equals(worldName)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Block block) {
        return block != null && contains(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ());
    }

    public boolean contains(Location location) {
        return location != null && location.getWorld() != null
                && contains(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Volume in blocks. Used only for display, so a long is plenty. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** The name to show, falling back to the id when no display name is set. */
    public String label() {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    public String boundsDescription() {
        return world + " (" + minX + "," + minY + "," + minZ + ")→(" + maxX + "," + maxY + "," + maxZ + ")";
    }
}
