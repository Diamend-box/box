package com.diamend.darksea.island;

import com.diamend.darksea.island.shape.DemoShape;
import com.diamend.darksea.island.shape.DemoShapes;
import com.diamend.darksea.loot.LootMath;
import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A placed island: where a template landed, its bounding box, and the world
 * positions of its loot chests and mob spawn points. Chest refill timestamps
 * live here too so they persist with the island.
 */
public final class IslandInstance {

    private final String id;
    private final String template;
    private final int tier;
    private final Pos origin;
    private final Pos min;
    private final Pos max;
    private final List<Pos> chests;
    private final List<Pos> spawnPoints;
    private final Map<String, Long> chestRefills = new HashMap<>();

    public IslandInstance(String id, String template, int tier, Pos origin, Pos min, Pos max,
                          List<Pos> chests, List<Pos> spawnPoints) {
        this.id = id;
        this.template = template;
        this.tier = tier;
        this.origin = origin;
        this.min = min;
        this.max = max;
        this.chests = List.copyOf(chests);
        this.spawnPoints = List.copyOf(spawnPoints);
    }

    public String id() {
        return id;
    }

    public String template() {
        return template;
    }

    public int tier() {
        return tier;
    }

    public Pos origin() {
        return origin;
    }

    public Pos min() {
        return min;
    }

    public Pos max() {
        return max;
    }

    public List<Pos> chests() {
        return chests;
    }

    /**
     * The built-in shape this island was raised from, or null for schematic
     * templates and the plain "demo" sand pads. Shape-specific behavior
     * (vault count, mob tier boost, wealth floor) hangs off this — the
     * registry only ever stores the template/shape id.
     */
    private DemoShape shape() {
        return DemoShapes.byId(template);
    }

    /**
     * The island's elected vault chests — the richer caches a multi-chest
     * island hides (landmark shapes elect two). Derived deterministically
     * from the origin, so the election never needs storing and a soft
     * reset always re-elects the same chests. Empty for islands with fewer
     * than two chests.
     */
    public List<Pos> vaultChests() {
        DemoShape shape = shape();
        int want = shape != null ? shape.vaultChestCount() : 1;
        int[] indices = LootMath.vaultChestIndices(origin.x(), origin.z(), chests.size(), want);
        List<Pos> vaults = new ArrayList<>(indices.length);
        for (int index : indices) {
            vaults.add(chests.get(index));
        }
        return vaults;
    }

    /** The first elected vault, or null when the island has none. */
    public Pos vaultChest() {
        List<Pos> vaults = vaultChests();
        return vaults.isEmpty() ? null : vaults.get(0);
    }

    /** Whether the given registered chest position is one of the vaults. */
    public boolean isVaultChest(Pos pos) {
        return vaultChests().contains(pos);
    }

    /**
     * The ring tier this island's mobs are drawn from: the ring itself,
     * plus the shape's boost — a ruined castle in ring 2 garrisons ring 3's
     * roster. Callers fall back to {@link #tier()} when no pool exists
     * this deep.
     */
    public int mobTier() {
        DemoShape shape = shape();
        return shape != null ? tier + shape.mobTierBoost() : tier;
    }

    /** Extra concurrent mobs this island holds beyond the configured cap. */
    public int mobCapBonus() {
        DemoShape shape = shape();
        return shape != null ? shape.mobCapBonus(tier) : 0;
    }

    /**
     * The resident boss this island keeps standing (a Mariphage Core), or null
     * for an ordinary island. A nest always has one; a fallen Naxome outpost
     * (a Trench castle or volcano) only sometimes did, so the shape's
     * {@link DemoShape#residentBossChance(int)} is rolled here against a stable
     * per-island seed — the same island always decides the same way, which is
     * what lets a soft reset re-raise (or not) the very same Core. The spawner
     * re-raises a present boss whenever the island is empty of one.
     */
    public String bossMob() {
        DemoShape shape = shape();
        if (shape == null) {
            return null;
        }
        String boss = shape.bossMob();
        if (boss == null) {
            return null;
        }
        double chance = shape.residentBossChance(tier);
        if (chance >= 1.0) {
            return boss;
        }
        if (chance <= 0.0) {
            return null;
        }
        return bossRoll() < chance ? boss : null;
    }

    /** A stable 0..1 roll for this island, keyed on its origin so it survives
     *  a soft reset (which never stores the decision, only the position). */
    private double bossRoll() {
        return new Random(DemoShapes.seedFor(origin.x(), origin.z()) * 1099511628211L
                + 0xB0550000B055L).nextDouble();
    }

    /** Vanilla fallback entity for {@link #bossMob()} where MythicMobs is absent. */
    public String bossFallback() {
        DemoShape shape = shape();
        return shape != null ? shape.bossFallback() : null;
    }

    /** Where the resident boss rises — the shape's first mob spawn, its heart. */
    public Pos bossSpawn() {
        return spawnPoints.isEmpty() ? null : spawnPoints.get(0);
    }

    /**
     * The island's Chronon wealth multiplier: the deterministic position
     * roll, floored by the shape's wealth floor (a castle never drowned
     * poor).
     */
    public double wealthMultiplier() {
        double wealth = LootMath.wealthMultiplier(origin.x(), origin.z());
        DemoShape shape = shape();
        return shape != null ? Math.max(wealth, shape.wealthFloor()) : wealth;
    }

    public List<Pos> spawnPoints() {
        return spawnPoints;
    }

    /** Epoch millis of the chest's last refill; 0 = never (refill on first open). */
    public long lastRefill(Pos chest) {
        Long last = chestRefills.get(chest.serialize());
        return last != null ? last : 0L;
    }

    public void setRefilled(Pos chest, long timestamp) {
        chestRefills.put(chest.serialize(), timestamp);
    }

    /** Soft reset: every chest becomes immediately refillable again. */
    public void clearRefills() {
        chestRefills.clear();
    }

    public void save(ConfigurationSection sec) {
        sec.set("template", template);
        sec.set("tier", tier);
        sec.set("origin", origin.serialize());
        sec.set("min", min.serialize());
        sec.set("max", max.serialize());
        sec.set("chests", chests.stream().map(Pos::serialize).toList());
        sec.set("spawn-points", spawnPoints.stream().map(Pos::serialize).toList());
        ConfigurationSection refills = sec.createSection("chest-refills");
        for (Map.Entry<String, Long> entry : chestRefills.entrySet()) {
            // "x,y,z" contains no YAML path separators, safe as a key.
            refills.set(entry.getKey().replace(',', ';'), entry.getValue());
        }
    }

    public static IslandInstance load(String id, ConfigurationSection sec) {
        List<Pos> chests = new ArrayList<>();
        for (String s : sec.getStringList("chests")) {
            chests.add(Pos.parse(s));
        }
        List<Pos> spawnPoints = new ArrayList<>();
        for (String s : sec.getStringList("spawn-points")) {
            spawnPoints.add(Pos.parse(s));
        }
        IslandInstance instance = new IslandInstance(id,
                sec.getString("template", "unknown"),
                sec.getInt("tier", 1),
                Pos.parse(sec.getString("origin", "0,0,0")),
                Pos.parse(sec.getString("min", "0,0,0")),
                Pos.parse(sec.getString("max", "0,0,0")),
                chests, spawnPoints);
        ConfigurationSection refills = sec.getConfigurationSection("chest-refills");
        if (refills != null) {
            for (String key : refills.getKeys(false)) {
                instance.chestRefills.put(key.replace(';', ','), refills.getLong(key));
            }
        }
        return instance;
    }
}
