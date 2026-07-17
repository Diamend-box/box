package com.diamend.darksea.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/** Deep ocean everywhere — uniform water color, ambient sea life, no land biomes. */
public final class OceanBiomeProvider extends BiomeProvider {

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Biome.DEEP_OCEAN;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(Biome.DEEP_OCEAN);
    }
}
