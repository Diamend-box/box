package com.diamend.robobear.data;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-player storage, one YAML file per UUID.
 *
 * <p>Writes happen off the main thread; the snapshot they write is always taken
 * on it. Nothing here runs during a block break — the run counter lives in
 * memory and only a finished round touches this class.
 */
public class DataManager {

    private final RoboBearPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataManager(RoboBearPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the playerdata folder at " + folder);
        }
    }

    public PlayerData get(Player player) {
        PlayerData data = cache.computeIfAbsent(player.getUniqueId(), this::read);
        data.setName(player.getName());
        return data;
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::read);
    }

    /** Loads a player's data on join, off the main thread. */
    public void loadAsync(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = read(uuid);
            data.setName(name);
            cache.put(uuid, data);
        });
    }

    /** Saves and forgets a player's data on quit. */
    public void unloadAsync(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.isDirty()) {
            Snapshot snapshot = snapshot(data);
            data.clean();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(snapshot));
        }
    }

    /** Writes every dirty cached record. Used by the autosave timer. */
    public void saveAllAsync() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                saveAsync(data);
            }
        }
    }

    /** Writes everything on the calling thread — for shutdown, where async is too late. */
    public void saveAllBlocking() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                write(snapshot(data));
                data.clean();
            }
        }
    }

    public void saveAsync(PlayerData data) {
        Snapshot snapshot = snapshot(data);
        data.clean();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(snapshot));
    }

    /** Wipes a player's record, online or off. */
    public void reset(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.clear();
            saveAsync(data);
            return;
        }
        File file = fileFor(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file);
        }
    }

    /** Resolves a name to a player the server has seen before, or null. */
    @SuppressWarnings("deprecation")
    public OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    // ------------------------------------------------------------------
    // Disk
    // ------------------------------------------------------------------

    private File fileFor(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    private PlayerData read(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        File file = fileFor(uuid);
        if (!file.exists()) {
            return data;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        data.setName(config.getString("name", ""));
        data.restore(
                config.getInt("runs"),
                config.getInt("best-round"),
                config.getInt("total-rounds"),
                config.getLong("last-run-at"),
                config.getLong("best-run-seconds"));

        ConfigurationSection milestones = config.getConfigurationSection("milestones");
        if (milestones != null) {
            for (String key : milestones.getKeys(false)) {
                try {
                    data.milestonesMap().put(Integer.parseInt(key), milestones.getInt(key));
                } catch (NumberFormatException ignored) {
                    // A hand-edited file with a bad key shouldn't lose the rest.
                }
            }
        }
        data.clean();
        return data;
    }

    /** An immutable copy taken on the main thread, safe to write from another. */
    private Snapshot snapshot(PlayerData data) {
        return new Snapshot(data.uuid(), data.getName(), data.runs(), data.bestRound(),
                data.totalRounds(), data.lastRunAt(), data.bestRunSeconds(),
                new HashMap<>(data.milestonesMap()));
    }

    private void write(Snapshot snapshot) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("name", snapshot.name());
        config.set("runs", snapshot.runs());
        config.set("best-round", snapshot.bestRound());
        config.set("total-rounds", snapshot.totalRounds());
        config.set("last-run-at", snapshot.lastRunAt());
        config.set("best-run-seconds", snapshot.bestRunSeconds());
        for (Map.Entry<Integer, Integer> entry : snapshot.milestones().entrySet()) {
            config.set("milestones." + entry.getKey(), entry.getValue());
        }
        try {
            config.save(fileFor(snapshot.uuid()));
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data for " + snapshot.uuid(), error);
        }
    }

    private record Snapshot(UUID uuid, String name, int runs, int bestRound, int totalRounds,
                            long lastRunAt, long bestRunSeconds,
                            Map<Integer, Integer> milestones) {
    }
}
