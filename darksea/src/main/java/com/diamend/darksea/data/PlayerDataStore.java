package com.diamend.darksea.data;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Per-player persistent state (playerdata/&lt;uuid&gt;.yml). Values are cached
 * after first read and written through immediately on change — the files are
 * tiny and changes are rare (boat upgrades).
 */
public final class PlayerDataStore {

    /**
     * A captain's spent stat points, one field per allocatable boat stat. Points
     * are earned 1 per boat level and spent from the boat wheel's Outfit page;
     * {@link #total()} is how many are committed (the rest are still free).
     */
    public record StatPoints(int speed, int toughness, int hp, int ramPower) {
        public static final StatPoints ZERO = new StatPoints(0, 0, 0, 0);

        public int total() {
            return speed + toughness + hp + ramPower;
        }
    }

    private final File folder;
    private final Logger log;
    private final Map<UUID, Integer> boatLevels = new ConcurrentHashMap<>();
    private final Map<UUID, StatPoints> statPoints = new ConcurrentHashMap<>();

    public PlayerDataStore(File folder, Logger log) {
        this.folder = folder;
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warning("Could not create " + folder);
        }
    }

    public int boatLevel(UUID player) {
        return boatLevels.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            if (!file.exists()) {
                return 0;
            }
            return YamlConfiguration.loadConfiguration(file).getInt("boat-level", 0);
        });
    }

    public void setBoatLevel(UUID player, int level) {
        boatLevels.put(player, level);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("boat-level", level);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    public StatPoints statPoints(UUID player) {
        return statPoints.computeIfAbsent(player, id -> {
            File file = fileFor(id);
            if (!file.exists()) {
                return StatPoints.ZERO;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            return new StatPoints(
                    yaml.getInt("stats.speed", 0),
                    yaml.getInt("stats.toughness", 0),
                    yaml.getInt("stats.hp", 0),
                    yaml.getInt("stats.ram-power", 0));
        });
    }

    public void setStatPoints(UUID player, StatPoints points) {
        statPoints.put(player, points);
        File file = fileFor(player);
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("stats.speed", points.speed());
        yaml.set("stats.toughness", points.toughness());
        yaml.set("stats.hp", points.hp());
        yaml.set("stats.ram-power", points.ramPower());
        try {
            yaml.save(file);
        } catch (IOException ex) {
            log.severe("Could not save " + file + ": " + ex.getMessage());
        }
    }

    private File fileFor(UUID player) {
        return new File(folder, player + ".yml");
    }
}
