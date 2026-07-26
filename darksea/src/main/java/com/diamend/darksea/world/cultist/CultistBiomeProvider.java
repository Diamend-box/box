package com.diamend.darksea.world.cultist;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

/**
 * One biome everywhere: basalt deltas, for the red murk and the ash falling
 * through it. The biome is doing atmosphere work rather than terrain work —
 * the caves' shape comes entirely from {@link CultistCarve} — and a nether
 * biome is what gets the fog, the ambient sound and the absence of weather
 * without needing a datapack to author a custom dimension.
 */
public final class CultistBiomeProvider extends BiomeProvider {

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        return Biome.BASALT_DELTAS;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(Biome.BASALT_DELTAS);
    }
}
