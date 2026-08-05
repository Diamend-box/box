package com.diamend.darksea.mob;

import com.diamend.darksea.DarkSeaPlugin;
import com.diamend.darksea.config.DarkSeaSettings;
import com.diamend.darksea.island.IslandInstance;
import com.diamend.darksea.island.IslandRegistry;
import com.diamend.darksea.util.Pos;
import io.lumine.mythic.api.exceptions.InvalidMobTypeException;
import io.lumine.mythic.bukkit.MythicBukkit;
import net.kyori.adventure.text.Component;
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
    /** When each island last noticed its resident boss missing, for the respawn wait. */
    private final Map<String, Long> bossFell = new HashMap<>();
    /**
     * Islands whose resident boss has stood at least once since the plugin
     * loaded. An island not in here has nothing to respawn, so the wait does
     * not apply to it — that distinction is the difference between a cooldown
     * and a nest that is simply empty.
     */
    private final Set<String> bossEverRaised = new HashSet<>();
    /** Per-island spawn budgets: what stops an island being an endless farm. */
    private final Map<String, SpawnBudget> budgets = new HashMap<>();
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

                SpawnBudget budget = budgets.computeIfAbsent(island.id(),
                        id -> new SpawnBudget(cfg.islandBudget() + island.mobCapBonus(),
                                cfg.budgetRefillMinutes() * 60_000L));
                if (budget.exhausted(now)) {
                    // Cleared out. Nothing more from this island until it
                    // refills — walking to the next one is the whole point.
                    continue;
                }

                // A nest keeps its boss standing, but not instantly. The
                // Order grows another once the sea has been empty of one for
                // a while — past the ordinary mob cap, never past the
                // island's budget.
                //
                // Without the wait this branch ran on every scan the moment
                // the last Core died, so a Trench castle handed out one boss
                // after another until its fourteen-mob budget was gone. That
                // is not a landmark defending itself, it is a queue, and it
                // is what the fourth live test walked into.
                String boss = island.bossMob();
                if (boss != null && countLive(mobs, boss) == 0) {
                    Long fellAt = bossFell.get(island.id());
                    if (fellAt == null && bossEverRaised.contains(island.id())) {
                        // It was standing when we last looked and is not now,
                        // so it has just died: start the wait.
                        bossFell.put(island.id(), now);
                        continue;
                    }
                    if (fellAt == null) {
                        // Never raised here at all. Waiting fifteen minutes to
                        // populate an island for the first time is not a
                        // respawn cooldown, it is an empty nest — which is
                        // exactly what the sixth playtest sailed to.
                        fellAt = 0L;
                    }
                    if (now - fellAt < cfg.bossRespawnMinutes() * 60_000L) {
                        continue;
                    }
                    Pos heart = island.bossSpawn();
                    if (heart != null && totalTracked() < cfg.globalCap()) {
                        UUID spawned = spawnMob(new MobPool.MobEntry(boss,
                                island.bossFallback(), 1, BOSS_LEVEL), heart.toLocation(world));
                        if (spawned != null) {
                            budget.tryConsume(now);
                            mobs.add(spawned);
                            bossFell.remove(island.id());
                            bossEverRaised.add(island.id());
                        }
                    }
                    continue;
                }
                if (boss != null) {
                    bossFell.remove(island.id());   // one is standing again
                    bossEverRaised.add(island.id());
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
                    // Charged only for a mob that actually stood up, so a
                    // broken mobs.yml entry cannot silently drain an island.
                    budget.tryConsume(now);
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
    private UUID spawnMob(MobPool.MobEntry entry, Location where) {
        Location location = standingRoom(where);
        if (location == null) {
            return null;   // nowhere here a mob could stand without being buried
        }
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
     * The nearest spot to a spawn point with enough clear air over it for a
     * big mob, or {@code null} if there is none.
     *
     * <p>Spawn points are single marker blocks, and a marker only guarantees
     * that <em>it</em> is clear. A Naxian Abomination is taller than one
     * block, so a marker under a low ceiling — a garrison hut, the underside
     * of a stair, the lip of a vault — put the mob's head inside stone and it
     * took suffocation damage from the moment it arrived. Anything that walked
     * out of its own accord was fine, which is why it only happened
     * "sometimes".
     *
     * <p>The search is deliberately small: straight up first, since the usual
     * miss is a marker one block too low, then the four neighbouring columns.
     * A spawn with nowhere to stand is skipped rather than nudged somewhere
     * arbitrary — a mob that appears across the island from its marker is a
     * worse bug than one that does not appear.
     */
    private Location standingRoom(Location at) {
        int clearance = plugin.settings().mobSpawning().spawnClearance();
        int width = plugin.settings().mobSpawning().spawnWidth();
        int[][] nudges = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int dy = 0; dy <= 2; dy++) {
            for (int[] nudge : nudges) {
                Location candidate = at.clone().add(nudge[0], dy, nudge[1]);
                Location room = clearBox(candidate, clearance, width);
                if (room != null) {
                    return room;
                }
            }
        }
        return null;
    }

    /**
     * Whether a box {@code width} columns on a side and {@code height} blocks
     * tall stands clear here, centred on this spot.
     *
     * <p>Height alone was not enough, and that is why the Abominations were
     * still suffocating after the last fix. A Ravager is about 1.95 blocks
     * wide as well as 2.2 tall, so it occupies two columns in each direction
     * whichever way it faces — a marker in a one-block-wide slot, a doorway or
     * the gap between two crenellations gives it all the headroom in the world
     * and still buries its flanks.
     */
    private Location clearBox(Location at, int height, int width) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        int span = Math.max(1, width);
        // The box has to include the marker column but need not be centred on
        // it, so try every placement that touches it — four for a 2-wide mob.
        // Requiring a symmetric 3x3 instead would be a block wider than a
        // Ravager actually is, and the cost of over-strictness here is an
        // island that spawns nothing.
        for (int ox = -(span - 1); ox <= 0; ox++) {
            for (int oz = -(span - 1); oz <= 0; oz++) {
                if (boxIsClear(world, at.getBlockX() + ox, at.getBlockY(),
                        at.getBlockZ() + oz, height, span)) {
                    // Centre of the box it actually fits in, so a mob two
                    // columns wide is not straddling the wall it just cleared.
                    return new Location(world,
                            at.getBlockX() + ox + span / 2.0,
                            at.getBlockY(),
                            at.getBlockZ() + oz + span / 2.0);
                }
            }
        }
        return null;
    }

    private boolean boxIsClear(World world, int x, int y, int z, int height, int span) {
        for (int dx = 0; dx < span; dx++) {
            for (int dz = 0; dz < span; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    if (!world.getBlockAt(x + dx, y + dy, z + dz).isPassable()) {
                        return false;
                    }
                }
            }
        }
        return true;
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
            nameIfAnonymous(entity, type);
        }
        return id;
    }

    /**
     * Gives a spawn a visible name derived from its mobs.yml type, unless it
     * already carries one — a Mythic mob named by the pack keeps the pack's
     * name, while a vanilla fallback stops being an anonymous husk. Without
     * this the whole roster reads identically to a player, which is what the
     * second live test reported.
     */
    private void nameIfAnonymous(Entity entity, String type) {
        if (entity.customName() != null) {
            return;
        }
        String name = MobNames.pretty(type);
        if (name.isEmpty()) {
            return;
        }
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(true);
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

        /** Whether Mythic has a mob registered under this internal name. */
        static boolean knows(String type) {
            return MythicBukkit.inst().getMobManager().getMythicMob(type).isPresent();
        }
    }

    /**
     * One line for {@code /ds diag}: is MythicMobs here, and does it actually
     * know the roster mobs.yml asks for? A pack sitting in the wrong folder
     * fails completely silently — every entry falls back to its vanilla mob,
     * the sea fills with anonymous husks, and nothing is ever logged, because
     * from the spawner's point of view the fallback worked. Reporting how
     * many ids resolve turns that into a number you can read at a glance.
     */
    public String mythicReport() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return "not installed — every mob spawns its vanilla fallback";
        }
        Set<String> types = new HashSet<>();
        for (List<MobPool.Slot> pool : pools.values()) {
            for (MobPool.Slot slot : pool) {
                types.add(slot.entry().type());
            }
        }
        if (types.isEmpty()) {
            return "installed, but mobs.yml declares no mobs";
        }
        int known = 0;
        for (String type : types) {
            if (MythicHook.knows(type)) {
                known++;
            }
        }
        String detail = known + " of " + types.size() + " mobs.yml ids known";
        if (known == 0) {
            return "installed, but " + detail + " — pack not in plugins/MythicMobs/Mobs/";
        }
        return known == types.size() ? "installed, " + detail : "installed, only " + detail;
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
        budgets.clear();
        bossFell.clear();
        bossEverRaised.clear();   // a reset sea has never raised anything
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
