package com.diamend.darksea.mob;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.util.Pos;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Proximity-triggered MythicMobs encounters. A slow task activates islands
 * with a player nearby and tops up their mob population one spawn per pass
 * (a gentle ramp instead of an instant wall of mobs), respecting per-island
 * and global caps. Islands abandoned for the configured cooldown despawn
 * their remaining tracked mobs.
 *
 * This class is the only place MythicMobs is touched, via the stable
 * {@code BukkitAPIHelper} entry point.
 */
public final class MobSpawner extends BukkitRunnable {

    public record MobEntry(String type, int weight, int level) {
    }

    private final DarkSeaPlugin plugin;
    private final IslandRegistry registry;
    private final Random rng = new Random();
    private final Map<String, Set<UUID>> tracked = new HashMap<>();
    private final Map<String, Long> lastNear = new HashMap<>();
    private final Set<String> warnedTypes = new HashSet<>();
    private volatile Map<Integer, List<MobEntry>> sets = Map.of();

    public MobSpawner(DarkSeaPlugin plugin, IslandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        reloadSets();
    }

    public void reloadSets() {
        File file = new File(plugin.getDataFolder(), "mobs.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<Integer, List<MobEntry>> loaded = new HashMap<>();
        ConfigurationSection tiers = yaml.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                int tier;
                try {
                    tier = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("mobs.yml tier '" + key + "' is not a number — skipped");
                    continue;
                }
                List<MobEntry> entries = new ArrayList<>();
                for (Map<?, ?> map : tiers.getMapList(key)) {
                    Object type = map.get("type");
                    if (type == null) {
                        continue;
                    }
                    entries.add(new MobEntry(String.valueOf(type),
                            Math.max(1, toInt(map.get("weight"), 1)),
                            Math.max(0, toInt(map.get("level"), 1))));
                }
                if (!entries.isEmpty()) {
                    loaded.put(tier, List.copyOf(entries));
                }
            }
        }
        this.sets = Map.copyOf(loaded);
    }

    @Override
    public void run() {
        DarkSeaSettings settings = plugin.settings();
        World world = Bukkit.getWorld(settings.worldName());
        if (world == null) {
            return;
        }
        DarkSeaSettings.MobSpawnSettings cfg = settings.mobSpawning();
        List<Player> players = world.getPlayers();
        double activationSq = cfg.activationRadius() * cfg.activationRadius();
        long now = System.currentTimeMillis();
        long cooldownMillis = cfg.abandonCooldownMinutes() * 60_000L;

        for (IslandInstance island : registry.all()) {
            Set<UUID> mobs = tracked.computeIfAbsent(island.id(), id -> new HashSet<>());
            prune(mobs);

            boolean near = false;
            for (Player player : players) {
                if (island.origin().distanceSquared2D(player.getLocation().getX(),
                        player.getLocation().getZ()) <= activationSq) {
                    near = true;
                    break;
                }
            }

            if (near) {
                lastNear.put(island.id(), now);
                if (island.spawnPoints().isEmpty() || mobs.size() >= cfg.perIslandCap()
                        || totalTracked() >= cfg.globalCap()) {
                    continue;
                }
                List<MobEntry> set = sets.get(island.tier());
                if (set == null || set.isEmpty()) {
                    continue;
                }
                MobEntry entry = weightedPick(set);
                Pos point = island.spawnPoints().get(rng.nextInt(island.spawnPoints().size()));
                UUID spawned = spawnMythic(entry, point.toLocation(world));
                if (spawned != null) {
                    mobs.add(spawned);
                }
            } else if (!mobs.isEmpty()) {
                Long last = lastNear.get(island.id());
                if (last == null || now - last > cooldownMillis) {
                    despawn(mobs);
                }
            }
        }
    }

    private UUID spawnMythic(MobEntry entry, Location location) {
        try {
            Entity entity = MythicBukkit.inst().getAPIHelper()
                    .spawnMythicMob(entry.type(), location, entry.level());
            return entity != null ? entity.getUniqueId() : null;
        } catch (InvalidMobTypeException ex) {
            if (warnedTypes.add(entry.type())) {
                plugin.getLogger().warning("mobs.yml references unknown MythicMobs type '"
                        + entry.type() + "' — it will never spawn (check your Mobs/*.yml internal names)");
            }
            return null;
        }
    }

    private MobEntry weightedPick(List<MobEntry> set) {
        int total = 0;
        for (MobEntry entry : set) {
            total += entry.weight();
        }
        int roll = rng.nextInt(total);
        for (MobEntry entry : set) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return set.get(set.size() - 1);
    }

    private void prune(Set<UUID> mobs) {
        Iterator<UUID> it = mobs.iterator();
        while (it.hasNext()) {
            Entity entity = Bukkit.getEntity(it.next());
            if (entity == null || entity.isDead()) {
                it.remove();
            }
        }
    }

    private void despawn(Set<UUID> mobs) {
        for (UUID id : mobs) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        mobs.clear();
    }

    /** Resets and shutdown: remove every mob this plugin spawned. */
    public void despawnAll() {
        for (Set<UUID> mobs : tracked.values()) {
            despawn(mobs);
        }
        tracked.clear();
        lastNear.clear();
    }

    private int totalTracked() {
        int total = 0;
        for (Set<UUID> mobs : tracked.values()) {
            total += mobs.size();
        }
        return total;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
