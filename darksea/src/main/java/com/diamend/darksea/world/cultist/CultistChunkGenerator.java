package com.diamend.darksea.world.cultist;

import com.diamend.darksea.config.DarkSeaSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Turns {@link CultistCarve}'s decisions into blocks. A thin shell around the
 * pure core — every "is there air here" question was already settled and
 * tested without a server; this only decides what stone to fill the rest with.
 *
 * <p>Like {@link com.diamend.darksea.world.OceanChunkGenerator}, vanilla
 * contributes nothing: no surface, no caves, no decorations, no structures, no
 * mobs. What the plugin writes is the whole world.
 *
 * <p>The box is sealed on all six sides — bedrock floor, bedrock roof, and
 * bedrock walls past the configured half-extent. The world border makes the
 * edge visible and pushes players back; the bedrock is what stops anyone
 * tunnelling under or over it, because a border alone does not.
 */
public final class CultistChunkGenerator extends ChunkGenerator {

    /** Roughly one floor block in twelve glows; one in twenty-five burns. */
    private static final int GLOW_ONE_IN = 12;
    private static final int MAGMA_ONE_IN = 25;

    private final int floorY;
    private final int roofY;
    private final int halfExtent;
    private final int chamberRadius;

    private volatile CultistCarve carve;
    private volatile long carveSeed;

    public CultistChunkGenerator(DarkSeaSettings.CultistSettings settings) {
        this(settings.floorY(), settings.roofY(), settings.halfExtent(),
                settings.chamberRadius());
    }

    public CultistChunkGenerator(int floorY, int roofY, int halfExtent, int chamberRadius) {
        this.floorY = floorY;
        this.roofY = roofY;
        this.halfExtent = halfExtent;
        this.chamberRadius = chamberRadius;
    }

    /**
     * The carve for this world, built lazily and rebuilt if the seed changes —
     * the same pattern the ocean generator uses, and for the same reason: the
     * generator is constructed before the world exists, so its seed is not
     * knowable at construction time.
     */
    private CultistCarve carveFor(WorldInfo info) {
        long seed = info.getSeed();
        CultistCarve current = carve;
        if (current == null || carveSeed != seed) {
            synchronized (this) {
                if (carve == null || carveSeed != seed) {
                    carve = new CultistCarve(seed, floorY, roofY, halfExtent, chamberRadius);
                    carveSeed = seed;
                }
                current = carve;
            }
        }
        return current;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random,
                              int chunkX, int chunkZ, ChunkData chunk) {
        CultistCarve shape = carveFor(worldInfo);
        int minY = chunk.getMinHeight();
        int maxY = Math.min(chunk.getMaxHeight() - 1, roofY + 4);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;

                if (!shape.inBounds(worldX, worldZ)) {
                    // Outside the box: solid bedrock top to bottom, so the
                    // border cannot be dug around, over or under.
                    for (int y = minY; y <= maxY; y++) {
                        chunk.setBlock(x, y, z, Material.BEDROCK);
                    }
                    continue;
                }

                chunk.setBlock(x, minY, z, Material.BEDROCK);
                for (int y = minY + 1; y <= floorY; y++) {
                    // The floor slab: bedrock at the very bottom of it too, so
                    // nobody mines through into the void.
                    chunk.setBlock(x, y, z,
                            y <= minY + 2 ? Material.BEDROCK : Material.BASALT);
                }

                // Open blocks are simply never written — a fresh ChunkData is
                // already air — so this only fills the rock around them.
                for (int y = floorY + 1; y < roofY; y++) {
                    if (!shape.isOpen(worldX, y, worldZ)) {
                        chunk.setBlock(x, y, z, fill(y));
                    }
                }

                for (int y = roofY; y <= maxY; y++) {
                    chunk.setBlock(x, y, z,
                            y >= roofY + 2 ? Material.BEDROCK : Material.BLACKSTONE);
                }

                // Light and hazard, dressed into the cave floor rather than
                // poured on top of it. Deliberately not lava: a generator can
                // only place a source block, and sources flow — seven blocks
                // in a nether world — so a "shallow sheet" would drain into
                // every passage below it and fill the place. Glowstone and
                // magma give the same read (lit floor, footing you watch)
                // and stay exactly where they are put.
                //
                // The arrival chamber is left plain: it sits at floorY + 1,
                // right in this band, and the portal drops people into it.
                int floorTop = floorAirBelow(shape, worldX, worldZ);
                if (floorTop != Integer.MIN_VALUE
                        && !shape.inChamber(worldX, floorTop + 1, worldZ)) {
                    int roll = scatter(worldX, worldZ);
                    if (roll % MAGMA_ONE_IN == 0) {
                        chunk.setBlock(x, floorTop, z, Material.MAGMA_BLOCK);
                    } else if (roll % GLOW_ONE_IN == 0) {
                        chunk.setBlock(x, floorTop, z, Material.GLOWSTONE);
                    }
                }
            }
        }
    }

    /**
     * The highest solid block in this column that has open cave directly above
     * it — the floor a player would be standing on. {@link Integer#MIN_VALUE}
     * when the column has no walkable floor at all (solid rock, or open all
     * the way up).
     */
    private int floorAirBelow(CultistCarve shape, int worldX, int worldZ) {
        for (int y = floorY + 1; y < roofY - 1; y++) {
            if (!shape.isOpen(worldX, y, worldZ) && shape.isOpen(worldX, y + 1, worldZ)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * A stable pseudo-random per column. A hash rather than a Random, because
     * this is called once per column during generation and must give the same
     * answer whenever that chunk is regenerated.
     */
    private static int scatter(int worldX, int worldZ) {
        int h = worldX * 0x27D4EB2D + worldZ * 0x165667B1;
        h ^= (h >>> 15);
        h *= 0x2545F491;
        h ^= (h >>> 13);
        return Math.abs(h);
    }

    /** What the solid rock is made of at a given height — a little banding. */
    private Material fill(int y) {
        if (y < floorY + 12) {
            return Material.BASALT;
        }
        if (y > roofY - 10) {
            return Material.BLACKSTONE;
        }
        return Material.NETHERRACK;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, floorY + 1, 0.5);
    }
}
