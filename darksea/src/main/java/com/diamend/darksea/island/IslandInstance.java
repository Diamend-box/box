package com.diamend.darksea.island;

import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
