package com.diamend.darksea.island;

import com.diamend.darksea.util.Pos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The single source of truth for what has been placed where (islands.yml).
 * Regeneration consults it so islands are never double-pasted; the chest
 * refill service and mob spawner look islands up through it. Main-thread
 * only.
 */
public final class IslandRegistry {

    /** A registered loot chest: which island it belongs to and where it is. */
    public record ChestRef(IslandInstance island, Pos pos) {
    }

    private final File file;
    private final Logger log;
    private final List<IslandInstance> islands = new ArrayList<>();
    private final Map<String, IslandInstance> byId = new HashMap<>();
    private final Map<String, ChestRef> chestIndex = new HashMap<>();
    private boolean spawnPasted;

    public IslandRegistry(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public void load() {
        islands.clear();
        byId.clear();
        chestIndex.clear();
        spawnPasted = false;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        spawnPasted = yaml.getBoolean("spawn-pasted", false);
        ConfigurationSection sec = yaml.getConfigurationSection("islands");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection islandSec = sec.getConfigurationSection(id);
                if (islandSec == null) {
                    continue;
                }
                try {
                    index(IslandInstance.load(id, islandSec));
                } catch (RuntimeException ex) {
                    log.warning("Skipping malformed island '" + id + "' in islands.yml: " + ex.getMessage());
                }
            }
        }
        log.info("Loaded " + islands.size() + " island(s) from islands.yml");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("spawn-pasted", spawnPasted);
        ConfigurationSection sec = yaml.createSection("islands");
        for (IslandInstance island : islands) {
            island.save(sec.createSection(island.id()));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save islands.yml: " + ex.getMessage());
        }
    }

    public void add(IslandInstance island) {
        index(island);
        save();
    }

    private void index(IslandInstance island) {
        islands.add(island);
        byId.put(island.id(), island);
        for (Pos chest : island.chests()) {
            chestIndex.put(chest.serialize(), new ChestRef(island, chest));
        }
    }

    public List<IslandInstance> all() {
        return List.copyOf(islands);
    }

    public List<IslandInstance> byTier(int tier) {
        List<IslandInstance> result = new ArrayList<>();
        for (IslandInstance island : islands) {
            if (island.tier() == tier) {
                result.add(island);
            }
        }
        return result;
    }

    public IslandInstance get(String id) {
        return byId.get(id);
    }

    public ChestRef chestAt(int x, int y, int z) {
        return chestIndex.get(x + "," + y + "," + z);
    }

    public boolean spawnPasted() {
        return spawnPasted;
    }

    public void setSpawnPasted(boolean pasted) {
        this.spawnPasted = pasted;
        save();
    }

    /** Full reset: forget everything (the world folder is gone too). */
    public void clearAll() {
        islands.clear();
        byId.clear();
        chestIndex.clear();
        spawnPasted = false;
        save();
    }

    /** Next free id for a tier, of the form t&lt;tier&gt;-&lt;n&gt;. */
    public String nextId(int tier) {
        int n = 1;
        while (byId.containsKey("t" + tier + "-" + n)) {
            n++;
        }
        return "t" + tier + "-" + n;
    }
}
