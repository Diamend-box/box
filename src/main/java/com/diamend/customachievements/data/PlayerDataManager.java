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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Loads and persists {@link PlayerData}. Data is cached in memory while a
 * player is online and written to {@code playerdata/<uuid>.yml}.
 *
 * <p>Writes are done off the main thread: the in-memory data is snapshotted into
 * a config on the calling (main) thread, then the file write is handed to a
 * single background thread so it can't stall the server or race with itself.
 * Shutdown saves are synchronous.
 */
public class PlayerDataManager {

    private final Plugin plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CustomAchievements-IO");
        thread.setDaemon(true);
        return thread;
    });

    public PlayerDataManager(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create playerdata folder.");
        }
    }

    public File folder() {
        return folder;
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

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void load(UUID uuid) {
        cache.put(uuid, loadFromDisk(uuid));
    }

    /** Loads data from disk without caching (used for offline admin operations). */
    public PlayerData loadDetached(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        return cached != null ? cached : loadFromDisk(uuid);
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
        List<?> pending = config.getList("pending-rewards");
        if (pending != null) {
            for (Object object : pending) {
                if (object instanceof org.bukkit.inventory.ItemStack item) {
                    data.getPendingRewards().add(item);
                }
            }
        }
        data.markClean();
        return data;
    }

    private YamlConfiguration snapshot(PlayerData data) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("completed", new ArrayList<>(data.getCompleted()));
        for (Map.Entry<String, Integer> entry : data.getProgressMap().entrySet()) {
            config.set("progress." + entry.getKey(), entry.getValue());
        }
        if (!data.getPendingRewards().isEmpty()) {
            config.set("pending-rewards", new ArrayList<>(data.getPendingRewards()));
        }
        return config;
    }

    private void writeToFile(YamlConfiguration config, File file, UUID uuid) {
        try {
            com.diamend.customachievements.util.AtomicYaml.save(config, file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data for " + uuid, ex);
        }
    }

    /** Snapshots the player's data on this thread and writes it in the background. */
    public void save(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        YamlConfiguration config = snapshot(data);
        File file = fileFor(uuid);
        data.markClean();
        io.execute(() -> writeToFile(config, file, uuid));
    }

    /** Writes the given (possibly detached) data synchronously. */
    public void saveNow(PlayerData data) {
        writeToFile(snapshot(data), fileFor(data.getUuid()), data.getUuid());
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

    /** Synchronously saves every cached player and stops the IO thread (shutdown). */
    public void saveAllAndShutdown() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            PlayerData data = cache.get(uuid);
            if (data != null) {
                saveNow(data);
            }
        }
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public List<PlayerData> cached() {
        return new ArrayList<>(cache.values());
    }

    /**
     * Reads every stored player's completed-achievement count (for a leaderboard).
     * Intended to be called off the main thread; cached (online) players override
     * the on-disk figures.
     *
     * <p>Only completions for achievements that still exist are counted: the
     * {@code validIds} set (lower-cased achievement ids, snapshotted on the main
     * thread) is intersected with each player's completed set, so achievements
     * that were deleted since a player earned them no longer inflate the totals.
     */
    public Map<UUID, Integer> completedCounts(java.util.Set<String> validIds) {
        Map<UUID, Integer> counts = new java.util.HashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    counts.put(uuid, countExisting(config.getStringList("completed"), validIds));
                } catch (RuntimeException ignored) {
                    // Skip non-UUID / unreadable files.
                }
            }
        }
        for (PlayerData data : cache.values()) {
            counts.put(data.getUuid(), countExisting(data.getCompleted(), validIds));
        }
        return counts;
    }

    /** Counts how many of the given completed ids still exist (case-insensitive). */
    private int countExisting(java.util.Collection<String> completed, java.util.Set<String> validIds) {
        if (validIds == null) {
            return completed.size();
        }
        int count = 0;
        for (String id : completed) {
            if (id != null && validIds.contains(id.toLowerCase(java.util.Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }
}
