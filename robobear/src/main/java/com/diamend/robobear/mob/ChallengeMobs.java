package com.diamend.robobear.mob;

import com.diamend.robobear.RoboBearPlugin;
import com.diamend.robobear.challenge.ObjectiveType;
import com.diamend.robobear.challenge.RoboRun;
import com.diamend.robobear.util.Items;
import com.diamend.robobear.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The things the challenge sends after you, and everything that keeps them
 * from becoming everybody else's problem.
 *
 * <p><b>Why these are real entities.</b> A mob that exists only as packets sent
 * to one player cannot attack: targeting, pathfinding and damage are all
 * server-side, and an entity the server doesn't have is a puppet with no hitbox.
 * So these are ordinary mobs, spawned properly, and the private part is done
 * with per-player visibility instead — {@link Player#hideEntity} for everyone
 * who isn't the owner, so the client is never sent the entity at all.
 *
 * <p><b>What that does and doesn't hide.</b> Nobody else renders them, can hit
 * them, or can be hit by them. Sound and particles are positional packets and
 * are not entity-scoped, so a bystander standing in the same mine will hear a
 * fight and see somebody taking damage from nothing. That is a real limit of
 * the approach rather than an oversight.
 *
 * <p>Mobs live only while a round's clock does. They are cleared when the round
 * ends, when the run ends, on death, logout, reload and shutdown, and anything a
 * crash strands is swept up when its chunk loads.
 */
public class ChallengeMobs {

    private final RoboBearPlugin plugin;

    /** Stamped into every spawned mob so a stray can be recognised after a crash. */
    private final NamespacedKey ownerKey;

    private final Map<UUID, UUID> ownerOf = new HashMap<>();
    private final Map<UUID, Set<UUID>> mobsOf = new HashMap<>();
    private final Map<UUID, Long> nextSpawnAt = new HashMap<>();

    private List<MobArchetype> roster = List.of();

    public ChallengeMobs(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "challenge_mob_owner");
        load();
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /** Re-reads the roster. Called on enable and on {@code /rb reload}. */
    public void load() {
        List<MobArchetype> parsed = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mobs.roster");
        if (section == null) {
            this.roster = List.of();
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) {
                continue;
            }
            EntityType type = entityType(entry.getString("type"));
            if (type == null) {
                plugin.getLogger().warning("Challenge mob '" + id + "' has an unknown type '"
                        + entry.getString("type") + "'; skipping it.");
                continue;
            }
            parsed.add(new MobArchetype(
                    id,
                    entry.getString("name", id),
                    type,
                    Math.max(1, entry.getInt("min-round", 1)),
                    Math.max(0, entry.getInt("weight", 1)),
                    Math.max(0.0, entry.getDouble("bonus-health", 0.0)),
                    entry.getBoolean("elite", false),
                    Items.material(entry.getString("held-item"), null)));
        }
        this.roster = List.copyOf(parsed);
    }

    private static EntityType entityType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Whether challenge mobs are switched on and there is anything to send. */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("mobs.enabled", true) && !roster.isEmpty();
    }

    public List<MobArchetype> roster() {
        return roster;
    }

    // ------------------------------------------------------------------
    // How many, and how fast
    // ------------------------------------------------------------------

    /** How many should be alive at once on this round. */
    public int population(int round, boolean killRound) {
        int base = plugin.getConfig().getInt("mobs.population.base", 2);
        double perRound = plugin.getConfig().getDouble("mobs.population.per-round", 0.5);
        int max = plugin.getConfig().getInt("mobs.population.max", 8);
        int bonus = killRound ? plugin.getConfig().getInt("mobs.population.kill-round-bonus", 2) : 0;

        int wanted = base + (int) Math.floor(perRound * Math.max(0, round - 1));
        return Math.max(1, Math.min(max, wanted) + bonus);
    }

    /** How long between reinforcements on this round, in seconds. */
    public int reinforceSeconds(int round, boolean killRound) {
        int base = plugin.getConfig().getInt("mobs.reinforce.base-seconds", 10);
        double perRound = plugin.getConfig().getDouble("mobs.reinforce.per-round", 0.5);
        int floor = Math.max(1, plugin.getConfig().getInt("mobs.reinforce.minimum-seconds", 3));

        int seconds = (int) Math.round(base - perRound * Math.max(0, round - 1));
        if (killRound) {
            seconds = Math.max(1, seconds / 2);
        }
        return Math.max(floor, seconds);
    }

    /**
     * The most kills the challenge itself could hand out in one round.
     *
     * <p>Used to size kill objectives. Vanilla mobs count towards them too, but
     * a mine world may have none at all, so the objective is built from what the
     * challenge can guarantee and anything natural is a bonus on top.
     *
     * @return the supply, or -1 when challenge mobs are switched off
     */
    public long supplyPerRound(int round) {
        if (!enabled()) {
            return -1;
        }
        int roundSeconds = plugin.getConfig().getInt("run.round-seconds", 300);
        int alive = population(round, true);
        int every = reinforceSeconds(round, true);
        return alive + (long) (roundSeconds / Math.max(1, every));
    }

    // ------------------------------------------------------------------
    // The round
    // ------------------------------------------------------------------

    /**
     * Sends the opening wave for a round.
     *
     * <p>Deliberately not instant. They spawn out of sight and walk in, after a
     * beat and a sound, rather than appearing in your face the moment the clock
     * starts.
     */
    public void beginRound(Player player, RoboRun run) {
        if (!enabled()) {
            return;
        }
        UUID owner = player.getUniqueId();
        endRound(owner); // never stack one round's wave on the last

        int delay = Math.max(0, plugin.getConfig().getInt("mobs.arrival.delay-seconds", 3));
        play(player, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f);
        plugin.messages().send(player, "mobs-incoming");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            RoboRun current = plugin.service().runOf(player);
            if (current != run || current.state() != RoboRun.State.RUNNING || !player.isOnline()) {
                return; // the round ended during the beat
            }
            boolean killRound = isKillRound(current);
            int wanted = population(current.round(), killRound);
            for (int i = 0; i < wanted; i++) {
                spawnOne(player, current);
            }
            if (plugin.milestones().at(current.round()) != null) {
                spawnElite(player, current);
            }
            nextSpawnAt.put(owner, System.currentTimeMillis()
                    + reinforceSeconds(current.round(), killRound) * 1000L);
        }, delay * 20L);
    }

    /** Clears one player's mobs. Safe to call when they have none. */
    public void endRound(UUID owner) {
        nextSpawnAt.remove(owner);
        Set<UUID> mine = mobsOf.remove(owner);
        if (mine == null) {
            return;
        }
        for (UUID mobId : mine) {
            ownerOf.remove(mobId);
            Entity mob = Bukkit.getEntity(mobId);
            if (mob != null) {
                mob.remove();
            }
        }
    }

    /**
     * Keeps every live wave honest: prunes the dead, drags stragglers back, and
     * sends reinforcements. Runs on a timer measured in ticks, not seconds.
     */
    public void tick() {
        if (mobsOf.isEmpty() && nextSpawnAt.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        for (UUID owner : new ArrayList<>(union(mobsOf.keySet(), nextSpawnAt.keySet()))) {
            Player player = Bukkit.getPlayer(owner);
            RoboRun run = player == null ? null : plugin.service().runOf(player);

            // The service clears waves itself; this is the backstop for a run
            // that ended by a route nobody told us about.
            if (player == null || !player.isOnline() || run == null
                    || run.state() != RoboRun.State.RUNNING) {
                endRound(owner);
                continue;
            }
            prune(owner);
            herd(player, owner);
            reinforce(player, run, owner, now);
        }
    }

    private static Set<UUID> union(Set<UUID> first, Set<UUID> second) {
        Set<UUID> all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }

    /** Forgets mobs that have died or been removed by something else. */
    private void prune(UUID owner) {
        Set<UUID> mine = mobsOf.get(owner);
        if (mine == null) {
            return;
        }
        mine.removeIf(mobId -> {
            Entity mob = Bukkit.getEntity(mobId);
            if (mob != null && mob.isValid()) {
                return false;
            }
            ownerOf.remove(mobId);
            return true;
        });
        if (mine.isEmpty()) {
            mobsOf.remove(owner);
        }
    }

    /**
     * Keeps the wave on top of its owner.
     *
     * <p>Mob AI won't path across a mine, let alone follow someone who has run
     * for the far side of the world, so anything that falls too far behind is
     * simply moved. Retargeting each pass matters as much as the teleport: a mob
     * that lost its target stands still, and a hazard that gives up isn't one.
     */
    private void herd(Player player, UUID owner) {
        Set<UUID> mine = mobsOf.get(owner);
        if (mine == null) {
            return;
        }
        double range = Math.max(4.0,
                plugin.getConfig().getDouble("mobs.follow.teleport-distance", 24.0));
        Location at = player.getLocation();

        for (UUID mobId : mine) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity == null || !entity.isValid()) {
                continue;
            }
            boolean elsewhere = !entity.getWorld().equals(at.getWorld());
            if (elsewhere || entity.getLocation().distanceSquared(at) > range * range) {
                Location spot = spotNear(at, 3.0, 6.0);
                entity.teleport(spot == null ? at : spot);
            }
            if (entity instanceof Mob mob) {
                LivingEntity target = mob.getTarget();
                if (target == null || !target.isValid() || !target.getUniqueId().equals(owner)) {
                    mob.setTarget(player);
                }
            }
        }
    }

    /** Tops the wave back up to strength, no faster than the round allows. */
    private void reinforce(Player player, RoboRun run, UUID owner, long now) {
        boolean killRound = isKillRound(run);
        int wanted = population(run.round(), killRound);
        int alive = mobsOf.getOrDefault(owner, Set.of()).size();
        if (alive >= wanted) {
            return;
        }
        Long due = nextSpawnAt.get(owner);
        if (due != null && now < due) {
            return;
        }
        spawnOne(player, run);
        nextSpawnAt.put(owner, now + reinforceSeconds(run.round(), killRound) * 1000L);
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    private void spawnOne(Player player, RoboRun run) {
        MobArchetype archetype = roll(run.round());
        if (archetype != null) {
            spawn(player, run, archetype);
        }
    }

    private void spawnElite(Player player, RoboRun run) {
        for (MobArchetype archetype : roster) {
            if (archetype.elite() && run.round() >= archetype.minRound()) {
                spawn(player, run, archetype);
                return;
            }
        }
    }

    /** Weighted pick among everything unlocked by this round. */
    private MobArchetype roll(int round) {
        int total = 0;
        for (MobArchetype archetype : roster) {
            if (archetype.availableAt(round)) {
                total += archetype.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        for (MobArchetype archetype : roster) {
            if (!archetype.availableAt(round)) {
                continue;
            }
            pick -= archetype.weight();
            if (pick < 0) {
                return archetype;
            }
        }
        return null;
    }

    private void spawn(Player player, RoboRun run, MobArchetype archetype) {
        double min = plugin.getConfig().getDouble("mobs.arrival.min-distance", 8.0);
        double max = plugin.getConfig().getDouble("mobs.arrival.max-distance", 16.0);
        Location where = spotNear(player.getLocation(), min, Math.max(min, max));
        if (where == null) {
            // Nowhere to stand out at range — a tight tunnel, most likely. Try
            // arm's length rather than skipping the wave entirely.
            where = spotNear(player.getLocation(), 2.0, 5.0);
        }
        if (where == null || where.getWorld() == null) {
            return;
        }

        Entity spawned;
        try {
            spawned = where.getWorld().spawnEntity(where, archetype.type());
        } catch (Throwable refused) {
            // Another plugin can veto a spawn in its region. That's its call.
            return;
        }
        if (spawned == null || !spawned.isValid()) {
            return;
        }

        dress(spawned, player, run, archetype);
        remember(spawned, player.getUniqueId());
        hideFromEveryoneBut(spawned, player.getUniqueId());
    }

    private void dress(Entity spawned, Player owner, RoboRun run, MobArchetype archetype) {
        spawned.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING,
                owner.getUniqueId().toString());
        spawned.setPersistent(false);
        spawned.customName(Text.parse(archetype.name()));
        spawned.setCustomNameVisible(
                plugin.getConfig().getBoolean("mobs.show-names", true));

        // Glow means "you have to kill this to get out of the round". On a
        // mining round the same mob is just a hazard, and being able to see it
        // coming through the rock would take the teeth out of it.
        spawned.setGlowing(isKillRound(run));

        if (spawned instanceof LivingEntity living) {
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(true);
            addHealth(living, archetype.bonusHealth());
            arm(living, archetype.heldItem());
        }
        if (spawned instanceof Mob mob) {
            mob.setTarget(owner);
        }
    }

    private static void addHealth(LivingEntity living, double bonus) {
        if (bonus <= 0) {
            return;
        }
        try {
            AttributeInstance health = living.getAttribute(Attribute.MAX_HEALTH);
            if (health == null) {
                return;
            }
            health.setBaseValue(health.getBaseValue() + bonus);
            living.setHealth(health.getValue());
        } catch (Throwable unsupported) {
            // Attribute keys move between versions; a mob at stock health is
            // a far better outcome than a wave that fails to spawn.
        }
    }

    private static void arm(LivingEntity living, Material held) {
        if (held == null) {
            return;
        }
        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItemInMainHand(new ItemStack(held));
        // Nothing a challenge mob carries should end up on the floor.
        equipment.setItemInMainHandDropChance(0f);
    }

    /**
     * Somewhere to put a mob: standable ground with room above it.
     *
     * <p>Bounded rather than exhaustive. If a dozen tries around the ring find
     * nothing, the caller falls back to a closer ring and then gives up for this
     * pass — a wave one mob short is not worth a search that scales with a mine.
     */
    private Location spotNear(Location origin, double min, double max) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = min + ThreadLocalRandom.current().nextDouble(Math.max(0.1, max - min));
            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int dy = 2; dy >= -3; dy--) {
                int y = origin.getBlockY() + dy;
                if (standable(world, x, y, z)) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean standable(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block floor = world.getBlockAt(x, y - 1, z);
        return feet.isPassable() && head.isPassable() && floor.getType().isSolid();
    }

    // ------------------------------------------------------------------
    // Who can see what
    // ------------------------------------------------------------------

    private void remember(Entity mob, UUID owner) {
        ownerOf.put(mob.getUniqueId(), owner);
        mobsOf.computeIfAbsent(owner, key -> new HashSet<>()).add(mob.getUniqueId());
    }

    private void hideFromEveryoneBut(Entity mob, UUID owner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(owner)) {
                online.hideEntity(plugin, mob);
            }
        }
    }

    /** Hides every live challenge mob from someone who has just joined. */
    public void hideAllFrom(Player viewer) {
        if (ownerOf.isEmpty()) {
            return;
        }
        UUID id = viewer.getUniqueId();
        for (Map.Entry<UUID, UUID> entry : ownerOf.entrySet()) {
            if (id.equals(entry.getValue())) {
                continue;
            }
            Entity mob = Bukkit.getEntity(entry.getKey());
            if (mob != null) {
                viewer.hideEntity(plugin, mob);
            }
        }
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    /** Whether this entity is one of ours, by tag rather than by bookkeeping. */
    public boolean isChallengeMob(Entity entity) {
        return ownerOf(entity) != null;
    }

    /**
     * Who a challenge mob belongs to, or null.
     *
     * <p>The live map is checked first and the tag second, so a mob left behind
     * by a crash is still recognised after a restart — which is what makes the
     * sweep able to clean up rather than leaving a hostile ghost in a mine.
     */
    public UUID ownerOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        UUID known = ownerOf.get(entity.getUniqueId());
        if (known != null) {
            return known;
        }
        String tag = entity.getPersistentDataContainer()
                .get(ownerKey, PersistentDataType.STRING);
        if (tag == null) {
            return null;
        }
        try {
            return UUID.fromString(tag);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Drops a dead mob from the books. */
    public void forget(Entity mob) {
        UUID owner = ownerOf.remove(mob.getUniqueId());
        if (owner == null) {
            return;
        }
        Set<UUID> mine = mobsOf.get(owner);
        if (mine != null) {
            mine.remove(mob.getUniqueId());
            if (mine.isEmpty()) {
                mobsOf.remove(owner);
            }
        }
    }

    public int liveCount() {
        return ownerOf.size();
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /** Removes every challenge mob we know about. For reload and shutdown. */
    public int despawnAll() {
        int removed = 0;
        for (UUID owner : new ArrayList<>(mobsOf.keySet())) {
            removed += mobsOf.getOrDefault(owner, Set.of()).size();
            endRound(owner);
        }
        ownerOf.clear();
        mobsOf.clear();
        nextSpawnAt.clear();
        return removed;
    }

    /**
     * Removes tagged mobs in a chunk that nobody is running a round for.
     *
     * <p>The bookkeeping lives in memory and a crash takes it with it, while the
     * mobs are still standing in the mine. The tag outlives the restart, so
     * anything wearing one that has no live wave behind it is a leftover.
     */
    public int sweep(Entity[] entities) {
        int removed = 0;
        for (Entity entity : entities) {
            UUID owner = ownerOf(entity);
            if (owner == null || ownerOf.containsKey(entity.getUniqueId())) {
                continue;
            }
            entity.remove();
            removed++;
        }
        return removed;
    }

    /** Sweeps every loaded chunk. Run on enable, after a crash. */
    public int sweepEverything() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += sweep(world.getEntities().toArray(new Entity[0]));
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isKillRound(RoboRun run) {
        return run.objective() != null && run.objective().type() == ObjectiveType.KILL_MOBS;
    }

    private void play(Player player, Sound sound, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, 0.8f, pitch);
        } catch (Throwable ignored) {
            // A missing sound key is not a reason to skip a wave.
        }
    }
}
