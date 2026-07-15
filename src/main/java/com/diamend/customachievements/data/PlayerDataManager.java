package com.diamend.customachievements.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and persists {@link PlayerData}. Data is cached in memory while a
 * player is online and written to {@code playerdata/<uuid>.yml}.
 */
public class PlayerDataManager {

    private final Plugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create playerdata folder.");
        }
    }

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    /** Returns the cached data for a player, loading it from disk if necessary. */
    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    public PlayerData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public void load(UUID uuid) {
        cache.put(uuid, loadFromDisk(uuid));
    }

    private PlayerData loadFromDisk(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        File file = fileFor(uuid);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.getCompleted().addAll(config.getStringList("completed"));
        if (config.isConfigurationSection("progress")) {
            for (String key : config.getConfigurationSection("progress").getKeys(false)) {
                data.getProgressMap().put(key, config.getInt("progress." + key));
            }
        }
        data.markClean();
        return data;
    }

    /** Persists a single player's data synchronously. */
    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("completed", new ArrayList<>(data.getCompleted()));
        for (Map.Entry<String, Integer> entry : data.getProgressMap().entrySet()) {
            config.set("progress." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(fileFor(uuid));
            data.markClean();
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + uuid, ex);
        }
    }

    /** Saves a player's data and removes them from the cache. */
    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    /** Saves every cached player that has unsaved changes. */
    public void saveAllDirty() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            PlayerData data = cache.get(uuid);
            if (data != null && data.isDirty()) {
                save(uuid);
            }
        }
    }

    /** Saves every cached player unconditionally (used on shutdown). */
    public void saveAll() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            save(uuid);
        }
    }

    public List<PlayerData> cached() {
        return new ArrayList<>(cache.values());
    }
}
