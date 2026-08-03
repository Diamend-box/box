package com.diamend.darksea.world;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.island.IslandPlacer;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.world.cultist.CultistBiomeProvider;
import com.diamend.darksea.world.cultist.CultistChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
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

    /** The cultist caves, or null if they have not been created yet. */
    public World caves() {
        return Bukkit.getWorld(plugin.settings().cultist().worldName());
    }

    /** Startup: make sure both worlds exist and the home island has landed. */
    public void init() {
        getOrCreate(null);
        getOrCreateCaves();
        placer.maybeQueueSpawn(Bukkit.getConsoleSender(), null);
    }

    /**
     * Creates the cultist caves if they are missing. Unlike the sea this world
     * is never reset — it is a designed space with hand-placed veins rather
     * than a rolled layout, and there is nothing in it that goes stale.
     */
    public World getOrCreateCaves() {
        DarkSeaSettings.CultistSettings caves = plugin.settings().cultist();
        World existing = Bukkit.getWorld(caves.worldName());
        if (existing != null) {
            return existing;
        }
        World world = new WorldCreator(caves.worldName())
                // NETHER for the atmosphere it brings for free: red fog, no
                // sky, no weather. The terrain is entirely ours.
                .environment(World.Environment.NETHER)
                .generateStructures(false)
                .generator(new CultistChunkGenerator(caves))
                .biomeProvider(new CultistBiomeProvider())
                .createWorld();
        if (world == null) {
            plugin.getLogger().severe("Could not create the caves world '"
                    + caves.worldName() + "'");
            return null;
        }
        applyCaveBorder(world, caves);
        world.setSpawnLocation(0, caves.floorY() + 1, 0);
        plugin.getLogger().info("Cultist caves '" + caves.worldName()
                + "' ready — " + (caves.halfExtent() * 2) + " blocks across");
        return world;
    }

    /**
     * The world border. It is the visible wall and the thing that pushes
     * players back; the generator's bedrock past the same line is what stops
     * anyone simply tunnelling around it, since a border does not affect
     * blocks at all.
     */
    private void applyCaveBorder(World world, DarkSeaSettings.CultistSettings caves) {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(caves.halfExtent() * 2.0);
        border.setWarningDistance(8);
        border.setDamageAmount(0.0);   // the bedrock already stops them; no need to hurt
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
            // Saved on the way out even though the next thing we do is delete
            // it. The delete refuses to touch a folder that does not look like
            // a world save, and an unsaved world may have no level.dat on disk
            // at all — a sea created this session and reset before the first
            // autosave failed that check and survived its own deletion.
            if (!Bukkit.unloadWorld(world, true)) {
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
        // One call, deliberately. generate() raises the home island and the
        // cultist landfall itself before it scatters the rings; queueing the
        // landfall separately here started the paste queue, and generate()
        // then refused itself as busy and placed nothing else.
        placer.generate(sender);
    }

    private void deleteWorldFolder(File folder) {
        String refusal = refuseToDelete(folder);
        if (refusal != null) {
            plugin.getLogger().warning("Refusing to delete " + folder.getAbsolutePath()
                    + " — " + refusal);
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

    /**
     * Why this folder must not be recursively deleted, or null if it is safe.
     *
     * <p>The guard exists so a mistyped {@code world-name} can never point a
     * recursive delete at something that is not a world save. It says which
     * check failed, because the previous version reported only that the folder
     * "does not look like the Dark Sea world" — and when it fired on the real
     * world folder there was no way to tell from the log whether the name or
     * the contents were at fault.
     *
     * <p>Any of {@code level.dat}, {@code region/} or {@code data/} is accepted
     * as proof this is a save. Requiring {@code level.dat} alone was too strict:
     * a world unloaded without saving may not have written one yet.
     */
    private String refuseToDelete(File folder) {
        if (!folder.isDirectory()) {
            return "not a directory";
        }
        String expected = plugin.settings().worldName();
        if (!folder.getName().equals(expected)) {
            return "folder is named '" + folder.getName() + "', not '" + expected + "'";
        }
        if (!new File(folder, "level.dat").exists()
                && !new File(folder, "region").isDirectory()
                && !new File(folder, "data").isDirectory()) {
            return "no level.dat, region/ or data/ inside — this is not a world save";
        }
        return null;
    }
}
