package com.diamend.darksea.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.Random;

/**
 * The endless ocean: still water from a noise-varied seabed up to sea level,
 * a gravel/sand floor over stone, bedrock at the bottom, no vanilla terrain,
 * caves, structures or decorations. Islands are pasted by the plugin, never
 * generated here.
 */
public final class OceanChunkGenerator extends ChunkGenerator {

    private final int seaLevel;
    private final int seabedBaseY;
    private final int seabedVariation;

    private volatile SimplexOctaveGenerator noise;
    private volatile long noiseSeed;

    public OceanChunkGenerator(int seaLevel, int seabedBaseY, int seabedVariation) {
        this.seaLevel = seaLevel;
        this.seabedBaseY = seabedBaseY;
        this.seabedVariation = Math.max(0, seabedVariation);
    }

    private SimplexOctaveGenerator noiseFor(WorldInfo info) {
        SimplexOctaveGenerator current = noise;
        if (current == null || noiseSeed != info.getSeed()) {
            synchronized (this) {
                if (noise == null || noiseSeed != info.getSeed()) {
                    SimplexOctaveGenerator created = new SimplexOctaveGenerator(new Random(info.getSeed()), 4);
                    created.setScale(1 / 128.0);
                    noiseSeed = info.getSeed();
                    noise = created;
                }
                current = noise;
            }
        }
        return current;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunk) {
        SimplexOctaveGenerator gen = noiseFor(worldInfo);
        int minY = chunk.getMinHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = (chunkX << 4) + x;
                int worldZ = (chunkZ << 4) + z;
                double n = gen.noise(worldX, worldZ, 0.5, 0.5, true);
                int floor = seabedBaseY + (int) Math.round(n * seabedVariation);
                floor = Math.max(minY + 4, Math.min(floor, seaLevel - 5));

                chunk.setBlock(x, minY, z, Material.BEDROCK);
                for (int y = minY + 1; y <= floor - 3; y++) {
                    chunk.setBlock(x, y, z, Material.STONE);
                }
                for (int y = Math.max(minY + 1, floor - 2); y < floor; y++) {
                    chunk.setBlock(x, y, z, Material.GRAVEL);
                }
                chunk.setBlock(x, floor, z, n > 0.35 ? Material.SAND : Material.GRAVEL);
                for (int y = floor + 1; y <= seaLevel; y++) {
                    chunk.setBlock(x, y, z, Material.WATER);
                }
            }
        }
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
        // Provisional; WorldService moves spawn on top of the home island
        // once it has been pasted.
        return new Location(world, 0.5, seaLevel + 1, 0.5);
    }
}
