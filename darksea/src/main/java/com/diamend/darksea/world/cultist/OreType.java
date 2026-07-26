package com.diamend.darksea.world.cultist;

/**
 * One kind of crystal vein, exactly as {@code ores.yml} describes it. Pure data —
 * blocks and drops are carried as ids and resolved at the Bukkit edge — so the
 * scatter, sizing and timing logic can be tested without a server.
 *
 * <p>A vein is a <em>geode</em>: a harvestable {@code coreBlock} blob wrapped in
 * a {@code shellBlock} rind that is studded with {@code budBlock}. Only the core
 * is mineable and only the core regenerates; the shell is scenery, placed once,
 * so a spent vein still reads as a geode waiting to refill.
 *
 * @param id            stable key, used in nodes.yml so a saved vein survives a
 *                      config edit that reorders the file
 * @param coreBlock     the harvestable crystal the vein is made of
 * @param shellBlock    the rind around the core
 * @param budBlock      crystal growths scattered through the rind
 * @param matrixBlock   what a mined-out core cell becomes until it regrows
 * @param budOneIn      one shell cell in this many is a bud
 * @param dropId        the item a mined core block yields
 * @param dropAmount    how many of that item per block
 * @param veinCount     how many veins of this type exist in the whole world
 * @param minSize       smallest vein core, in blocks
 * @param maxSize       largest vein core, in blocks
 * @param mineSeconds   time to extract one block <em>at reference gear</em>; see
 *                      {@link MiningSpeed} for why the reference framing matters
 * @param cooldownMinutes  how long after the <em>first</em> block is mined before
 *                      the whole vein grows back
 */
public record OreType(String id, String coreBlock, String shellBlock, String budBlock,
                      String matrixBlock, int budOneIn, String dropId, int dropAmount,
                      int veinCount, int minSize, int maxSize, double mineSeconds,
                      long cooldownMinutes) {

    public OreType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ore type needs an id");
        }
        if (coreBlock == null || coreBlock.isBlank()) {
            throw new IllegalArgumentException(id + ": needs a block");
        }
        if (dropId == null || dropId.isBlank()) {
            throw new IllegalArgumentException(id + ": needs a drop");
        }
        // The rind is cosmetic, so a missing shell or bud degrades to "no rind"
        // rather than costing the server a vein it could otherwise have placed.
        shellBlock = blankToNull(shellBlock);
        budBlock = blankToNull(budBlock);
        matrixBlock = matrixBlock == null || matrixBlock.isBlank() ? "BASALT" : matrixBlock;
        budOneIn = Math.max(0, budOneIn);
        dropAmount = Math.max(1, dropAmount);
        veinCount = Math.max(0, veinCount);
        minSize = Math.max(1, minSize);
        maxSize = Math.max(minSize, maxSize);
        mineSeconds = Math.max(MiningSpeed.FLOOR_SECONDS_MIN, mineSeconds);
        cooldownMinutes = Math.max(1, cooldownMinutes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * A type carrying the standard geode dressing — calcite rind, amethyst buds,
     * basalt matrix. Exists because the canonical constructor takes thirteen
     * arguments and only five of them are ever interesting; call sites that care
     * about counts and timings should not have to restate the rind every time.
     */
    public static OreType of(String id, String coreBlock, String dropId, int veinCount,
                             int minSize, int maxSize, double mineSeconds,
                             long cooldownMinutes) {
        return new OreType(id, coreBlock, "CALCITE", "AMETHYST_CLUSTER", "BASALT", 7,
                dropId, 1, veinCount, minSize, maxSize, mineSeconds, cooldownMinutes);
    }

    public long cooldownMillis() {
        return cooldownMinutes * 60_000L;
    }

    /** Whether this type draws a geode rind at all. */
    public boolean hasShell() {
        return shellBlock != null;
    }

    /** The size this type's vein core takes for a given seed. */
    public int sizeFor(long seed) {
        return OreVein.sizeFor(seed, minSize, maxSize);
    }
}
