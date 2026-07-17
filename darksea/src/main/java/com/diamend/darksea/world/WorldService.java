package com.diamend.darksea.world;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.island.IslandPlacer;
import com.diamend.darksea.island.IslandRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Creates and owns the dark_sea world, and implements both reset flavors.
 * The world is cheap to throw away by design: the ocean is procedural, the
 * islands are schematics plus a registry file — deleting the folder and
 * re-rolling costs nothing but paste time and yields a brand-new layout.
 */
public final class WorldService {

    private final DarkSeaPlugin plugin;
    private final IslandRegistry registry;
    private final IslandPlacer placer;

    public WorldService(DarkSeaPlugin plugin, IslandRegistry registry, IslandPlacer placer) {
        this.plugin = plugin;
        this.registry = registry;
        this.placer = placer;
    }

    public World world() {
        return Bukkit.getWorld(plugin.settings().worldName());
    }

    /** Startup: make sure the world exists and the home island has landed. */
    public void init() {
        getOrCreate(null);
        placer.maybeQueueSpawn(Bukkit.getConsoleSender(), null);
    }

    private World getOrCreate(Long seed) {
        DarkSeaSettings settings = plugin.settings();
        World existing = Bukkit.getWorld(settings.worldName());
        if (existing != null) {
            return existing;
        }
        WorldCreator creator = new WorldCreator(settings.worldName())
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                .generator(new OceanChunkGenerator(settings.seaLevel(),
                        settings.seabedBaseY(), settings.seabedVariation()))
                .biomeProvider(new OceanBiomeProvider());
        if (seed != null) {
            creator.seed(seed);
        }
        World world = creator.createWorld();
        plugin.getLogger().info("Dark Sea world '" + settings.worldName() + "' ready");
        return world;
    }

    /**
     * Soft reset: keep the map, re-paste every island (and the home island)
     * over itself from its schematic. Heals damage, restores chests, clears
     * refill timestamps; tracked mobs are despawned so encounters respawn.
     */
    public void resetSoft(CommandSender sender) {
        if (placer.busy()) {
            plugin.messages().send(sender, "generate-busy");
            return;
        }
        plugin.mobSpawner().despawnAll();
        placer.repasteAll(sender);
    }

    /**
     * Full reset: evacuate, delete the world and the registry, recreate with
     * a fresh seed, re-paste the home island and re-run generation — same
     * templates, brand-new layout.
     */
    public void resetFull(CommandSender sender) {
        if (placer.busy()) {
            plugin.messages().send(sender, "generate-busy");
            return;
        }
        World world = world();
        if (world != null) {
            World fallback = Bukkit.getWorlds().get(0);
            if (fallback == world) {
                plugin.messages().send(sender, "reset-unload-failed");
                return;
            }
            for (Player player : world.getPlayers()) {
                player.teleport(fallback.getSpawnLocation());
            }
            plugin.mobSpawner().despawnAll();

            File folder = world.getWorldFolder();
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.messages().send(sender, "reset-unload-failed");
                return;
            }
            registry.clearAll();
            plugin.messages().send(sender, "reset-full-started");

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                deleteWorldFolder(folder);
                Bukkit.getScheduler().runTask(plugin, () -> recreateAndRegenerate(sender));
            });
        } else {
            registry.clearAll();
            plugin.messages().send(sender, "reset-full-started");
            recreateAndRegenerate(sender);
        }
    }

    private void recreateAndRegenerate(CommandSender sender) {
        getOrCreate(new SecureRandom().nextLong());
        // generate() pastes the home island first, then scatters the rings.
        placer.generate(sender);
    }

    private void deleteWorldFolder(File folder) {
        // Only ever delete something that actually looks like our world save.
        if (!folder.getName().equals(plugin.settings().worldName())
                || !new File(folder, "level.dat").exists()) {
            plugin.getLogger().warning("Refusing to delete " + folder + " — does not look like the Dark Sea world");
            return;
        }
        try (Stream<Path> walk = Files.walk(folder.toPath())) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not delete " + path + ": " + ex.getMessage());
                }
            });
        } catch (IOException ex) {
            plugin.getLogger().severe("Deleting world folder failed: " + ex.getMessage());
        }
    }
}
