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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
 * MythicMobs is optional (a softdepend): the nested {@link MythicHook} is
 * the only code that references its classes, and it is never loaded unless
 * the plugin is actually present — every entry then spawns its vanilla
 * fallback instead.
 */
public final class MobSpawner extends BukkitRunnable {

    /** MythicMobs level for a resident boss; the shipped pack uses flat stats. */
    private static final int BOSS_LEVEL = 10;

    private final DarkSeaPlugin plugin;
    private final IslandRegistry registry;
    private final Random rng = new Random();
    private final Map<String, Set<UUID>> tracked = new HashMap<>();
    private final Map<String, Long> lastNear = new HashMap<>();
    private final Set<String> warnedTypes = new HashSet<>();
    private volatile Map<Integer, List<MobPool.Slot>> pools = Map.of();

    public MobSpawner(DarkSeaPlugin plugin, IslandRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        reloadSets();
    }

    /**
     * Re-reads mobs.yml. Each entry's tier is its minimum ring; the parsed
     * sets are expanded by {@link MobPool#build} so lower-tier mobs also roam
     * deeper rings, decayed by {@code lower-tier-decay} per ring of distance.
     */
    public void reloadSets() {
        File file = new File(plugin.getDataFolder(), "mobs.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<Integer, List<MobPool.MobEntry>> loaded = new HashMap<>();
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
                List<MobPool.MobEntry> entries = new ArrayList<>();
                for (Map<?, ?> map : tiers.getMapList(key)) {
                    Object type = map.get("type");
                    if (type == null) {
                        continue;
                    }
                    Object fallback = map.get("fallback");
                    entries.add(new MobPool.MobEntry(String.valueOf(type),
                            fallback != null ? String.valueOf(fallback) : null,
                            Math.max(1, toInt(map.get("weight"), 1)),
                            Math.max(0, toInt(map.get("level"), 1))));
                }
                if (!entries.isEmpty()) {
                    loaded.put(tier, List.copyOf(entries));
                }
            }
        }
        this.pools = MobPool.build(loaded, yaml.getDouble("lower-tier-decay", MobPool.DEFAULT_DECAY));
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

                // A nest keeps its boss standing: the Order grows another the
                // moment the sea is empty of one, even past the ordinary mob
                // cap. One spawn per pass keeps the same gentle ramp.
                String boss = island.bossMob();
                if (boss != null && countLive(mobs, boss) == 0) {
                    Pos heart = island.bossSpawn();
                    if (heart != null && totalTracked() < cfg.globalCap()) {
                        UUID spawned = spawnMob(new MobPool.MobEntry(boss,
                                island.bossFallback(), 1, BOSS_LEVEL), heart.toLocation(world));
                        if (spawned != null) {
                            mobs.add(spawned);
                        }
                    }
                    continue;
                }

                if (island.spawnPoints().isEmpty()
                        || mobs.size() >= cfg.perIslandCap() + island.mobCapBonus()
                        || totalTracked() >= cfg.globalCap()) {
                    continue;
                }
                // Landmark shapes garrison a deeper ring's roster; fall back
                // to the island's own ring where no deeper pool exists.
                List<MobPool.Slot> pool = pools.get(island.mobTier());
                if (pool == null || pool.isEmpty()) {
                    pool = pools.get(island.tier());
                }
                if (pool == null || pool.isEmpty()) {
                    continue;
                }
                MobPool.MobEntry entry = MobPool.pick(pool, rng);
                Pos point = island.spawnPoints().get(rng.nextInt(island.spawnPoints().size()));
                UUID spawned = spawnMob(entry, point.toLocation(world));
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

    /**
     * Tries the entry's {@code type} as a MythicMobs internal name (only when
     * that plugin is present), then spawns the vanilla fallback: {@code
     * fallback} if set, otherwise {@code type} itself read as a vanilla
     * entity. A server without MythicMobs still gets working encounters.
     */
    private UUID spawnMob(MobPool.MobEntry entry, Location location) {
        if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            UUID mythic = MythicHook.spawn(entry.type(), location, entry.level());
            if (mythic != null) {
                return tagSpawn(mythic, entry.type());
            }
        }
        UUID vanilla = spawnVanilla(entry.vanillaName(), location);
        if (vanilla != null) {
            return tagSpawn(vanilla, entry.type());
        }
        if (warnedTypes.add(entry.type())) {
            plugin.getLogger().warning("mobs.yml type '" + entry.type() + "' (vanilla fallback '"
                    + entry.vanillaName() + "') is neither a MythicMobs mob nor a vanilla entity"
                    + " — it will never spawn");
        }
        return null;
    }

    /**
     * Stamps the spawned entity with its mobs.yml type so the drop service
     * recognizes it at death — identically for Mythic mobs and vanilla
     * fallbacks, which is what keeps signature drops working on servers
     * without MythicMobs.
     */
    private UUID tagSpawn(UUID id, String type) {
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.getPersistentDataContainer().set(MobDrops.MOB_ID_KEY,
                    PersistentDataType.STRING, type);
        }
        return id;
    }

    private UUID spawnVanilla(String name, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Class<? extends Entity> clazz = type.getEntityClass();
        if (clazz == null || !LivingEntity.class.isAssignableFrom(clazz)) {
            return null;  // not a spawnable living mob (item frames, projectiles, ...)
        }
        try {
            return world.spawnEntity(location, type).getUniqueId();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * The only code that references MythicMobs classes. Nested so the JVM
     * never links it when the plugin isn't installed — callers must check
     * {@code isPluginEnabled("MythicMobs")} first.
     */
    private static final class MythicHook {

        private MythicHook() {
        }

        /** Returns the spawned mob's id, or null when Mythic can't provide it. */
        static UUID spawn(String type, Location location, int level) {
            try {
                Entity entity = MythicBukkit.inst().getAPIHelper()
                        .spawnMythicMob(type, location, level);
                return entity != null ? entity.getUniqueId() : null;
            } catch (InvalidMobTypeException ex) {
                return null;
            }
        }
    }

    /** How many still-living tracked mobs carry the given mobs.yml type tag. */
    private int countLive(Set<UUID> mobs, String type) {
        int count = 0;
        for (UUID id : mobs) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && !entity.isDead() && type.equals(entity
                    .getPersistentDataContainer().get(MobDrops.MOB_ID_KEY, PersistentDataType.STRING))) {
                count++;
            }
        }
        return count;
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
