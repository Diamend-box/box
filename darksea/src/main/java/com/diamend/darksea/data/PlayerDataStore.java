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

    private final File folder;
    private final Logger log;
    private final Map<UUID, Integer> boatLevels = new ConcurrentHashMap<>();

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

    private File fileFor(UUID player) {
        return new File(folder, player + ".yml");
    }
}
