package com.diamend.darksea.world.cultist;

/**
 * One kind of ore vein, exactly as {@code ores.yml} describes it. Pure data —
 * the block and drop are carried as ids and resolved at the Bukkit edge — so
 * the scatter and sizing logic can be tested without a server.
 *
 * @param id           stable key, used in nodes.yml so a saved vein survives a
 *                     config edit that reorders the file
 * @param blockId      the block the vein is made of
 * @param dropId       the item a mined block yields
 * @param dropAmount   how many of that item per block
 * @param veinCount    how many veins of this type exist in the whole world
 * @param minSize      smallest vein, in blocks
 * @param maxSize      largest vein, in blocks
 * @param cooldownMinutes  how long after the last block is mined before the
 *                     whole vein grows back
 */
public record OreType(String id, String blockId, String dropId, int dropAmount,
                      int veinCount, int minSize, int maxSize, long cooldownMinutes) {

    public OreType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ore type needs an id");
        }
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException(id + ": needs a block");
        }
        if (dropId == null || dropId.isBlank()) {
            throw new IllegalArgumentException(id + ": needs a drop");
        }
        dropAmount = Math.max(1, dropAmount);
        veinCount = Math.max(0, veinCount);
        minSize = Math.max(1, minSize);
        maxSize = Math.max(minSize, maxSize);
        cooldownMinutes = Math.max(1, cooldownMinutes);
    }

    public long cooldownMillis() {
        return cooldownMinutes * 60_000L;
    }

    /** The size this type's vein takes for a given seed. */
    public int sizeFor(long seed) {
        return OreVein.sizeFor(seed, minSize, maxSize);
    }
}
