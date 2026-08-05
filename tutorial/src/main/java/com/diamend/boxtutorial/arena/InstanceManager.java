package com.diamend.boxtutorial.arena;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hands out arenas.
 *
 * <p>The tutorial world is made on first use rather than at startup, so a
 * server that never runs the tutorial never pays for the world, and a test can
 * put its own world there first.
 */
public class InstanceManager {

    private final Plugin plugin;
    private final ArenaBlueprint blueprint;

    private final Map<Integer, Instance> instances = new LinkedHashMap<>();
    private final Map<UUID, Integer> byOwner = new HashMap<>();

    private World world;

    public InstanceManager(Plugin plugin, ArenaBlueprint blueprint) {
        this.plugin = plugin;
        this.blueprint = blueprint;
    }

    // ------------------------------------------------------------------
    // The world
    // ------------------------------------------------------------------

    /** The tutorial world, loading or creating it the first time it's needed. */
    public World world() {
        if (world != null && Bukkit.getWorld(world.getName()) != null) {
            return world;
        }
        World existing = Bukkit.getWorld(blueprint.worldName());
        if (existing != null) {
            world = existing;
            prepare(world);
            return world;
        }
        try {
            world = new WorldCreator(blueprint.worldName())
                    .generator(new VoidGenerator())
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not create the tutorial world '"
                    + blueprint.worldName() + "'", ex);
            world = null;
            return null;
        }
        if (world != null) {
            plugin.getLogger().info("Created the tutorial world '" + world.getName() + "'.");
            prepare(world);
        }
        return world;
    }

    public boolean isArenaWorld(World candidate) {
        return candidate != null && candidate.getName().equals(blueprint.worldName());
    }

    /** Permanent noon, no weather, no mobs, no fall damage. */
    private void prepare(World target) {
        try {
            target.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            target.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            target.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            target.setGameRule(GameRule.DO_FIRE_TICK, false);
            target.setGameRule(GameRule.MOB_GRIEFING, false);
            target.setGameRule(GameRule.FALL_DAMAGE, false);
            target.setGameRule(GameRule.KEEP_INVENTORY, true);
            target.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            target.setTime(6000L);
            target.setStorm(false);
            target.setThundering(false);
            target.setDifficulty(Difficulty.PEACEFUL);
            target.setAutoSave(false);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not set up the tutorial world: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Instances
    // ------------------------------------------------------------------

    /**
     * Gives this player an arena, building it fresh.
     *
     * @return their instance, or null when the world is unavailable or every
     *         instance is taken
     */
    public Instance claim(Player player) {
        Instance already = of(player.getUniqueId());
        if (already != null) {
            return already;
        }
        World arena = world();
        if (arena == null) {
            return null;
        }
        for (int index = 0; index < blueprint.maxInstances(); index++) {
            Instance instance = instances.computeIfAbsent(index,
                    key -> new Instance(plugin, blueprint, arena, key));
            if (!instance.isFree()) {
                continue;
            }
            instance.setOwner(player.getUniqueId());
            byOwner.put(player.getUniqueId(), index);
            instance.build();
            return instance;
        }
        return null;
    }

    public Instance of(UUID uuid) {
        Integer index = byOwner.get(uuid);
        return index == null ? null : instances.get(index);
    }

    public Instance of(Player player) {
        return player == null ? null : of(player.getUniqueId());
    }

    /** The instance a location sits in, whoever owns it. */
    public Instance at(Location location) {
        if (location == null || !isArenaWorld(location.getWorld())) {
            return null;
        }
        int index = (int) Math.round(location.getX() / blueprint.spacing());
        Instance instance = instances.get(index);
        return instance != null && instance.contains(location) ? instance : null;
    }

    /** Hands an instance back so the next player can have it. */
    public void release(UUID uuid) {
        Integer index = byOwner.remove(uuid);
        if (index == null) {
            return;
        }
        Instance instance = instances.get(index);
        if (instance != null) {
            instance.release();
        }
    }

    public Collection<Instance> all() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public int inUse() {
        return byOwner.size();
    }

    public int capacity() {
        return blueprint.maxInstances();
    }

    /** Clears every instance — on reload, and on shutdown. */
    public void releaseAll() {
        for (Instance instance : instances.values()) {
            instance.release();
        }
        byOwner.clear();
    }
}
