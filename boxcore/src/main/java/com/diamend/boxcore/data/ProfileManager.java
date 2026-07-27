package com.diamend.boxcore.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Loads and persists {@link PlayerProfile}s to {@code playerdata/<uuid>.yml}.
 *
 * <p>Profiles are cached in memory while a player is online. Writes snapshot the
 * profile on the calling (main) thread and hand the file write to a single
 * background thread, so saving can neither stall a tick nor race with itself.
 * Shutdown saves are synchronous.
 */
public class ProfileManager {

    /**
     * Path separator for the stored files.
     *
     * <p>Node keys are {@code tree.node}, and the default separator is a dot —
     * so {@code set("nodes." + key, …)} would silently nest
     * {@code combat.toughness} into {@code nodes → combat → toughness} and read
     * back as nothing. Switching the separator keeps dotted keys literal.
     */
    private static final char SEPARATOR = '/';

    private final Plugin plugin;
    private final File folder;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BoxCore-IO");
        thread.setDaemon(true);
        return thread;
    });

    public ProfileManager(Plugin plugin) {
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

    /** Returns the cached profile, loading it from disk if necessary. */
    public PlayerProfile get(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::read);
    }

    public PlayerProfile getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void load(UUID uuid) {
        cache.put(uuid, read(uuid));
    }

    /** Returns the cached profile, or reads one from disk without caching it. */
    public PlayerProfile loadDetached(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        return cached != null ? cached : read(uuid);
    }

    /** True when this player has never been seen by BoxCore before. */
    public boolean isNew(UUID uuid) {
        return !cache.containsKey(uuid) && !fileFor(uuid).exists();
    }

    private PlayerProfile read(UUID uuid) {
        PlayerProfile profile = new PlayerProfile(uuid);
        awaitPendingWrites();
        File file = fileFor(uuid);
        if (!file.exists()) {
            return profile;
        }
        // The separator has to be set *before* parsing: loading walks the YAML
        // tree calling set() with each raw key, so a key holding a dot would be
        // re-nested on the way in even if we fixed the separator afterwards.
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator(SEPARATOR);
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read BoxCore profile for " + uuid, ex);
            return profile;
        }
        profile.setName(config.getString("name", ""));
        profile.setPointsEarned(config.getInt("points/earned"));
        profile.setPointsSpent(config.getInt("points/spent"));
        profile.setPlaytimePointsGranted(config.getInt("points/playtime-granted"));

        ConfigurationSection nodes = config.getConfigurationSection("nodes");
        if (nodes != null) {
            for (String key : nodes.getKeys(false)) {
                profile.setNodeLevel(key, nodes.getInt(key));
            }
        }
        ConfigurationSection collections = config.getConfigurationSection("collections");
        if (collections != null) {
            for (String key : collections.getKeys(false)) {
                profile.setCollected(key, collections.getLong(key + SEPARATOR + "amount"));
                profile.setAwardedTier(key, collections.getInt(key + SEPARATOR + "tier"));
            }
        }
        profile.markClean();
        return profile;
    }

    private YamlConfiguration snapshot(PlayerProfile profile) {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator(SEPARATOR);
        config.set("name", profile.getName());
        config.set("points/earned", profile.getPointsEarned());
        config.set("points/spent", profile.getPointsSpent());
        config.set("points/playtime-granted", profile.getPlaytimePointsGranted());
        for (Map.Entry<String, Integer> entry : profile.getNodes().entrySet()) {
            config.set("nodes" + SEPARATOR + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Long> entry : profile.getCollections().entrySet()) {
            String base = "collections" + SEPARATOR + entry.getKey() + SEPARATOR;
            config.set(base + "amount", entry.getValue());
            config.set(base + "tier", profile.getAwardedTier(entry.getKey()));
        }
        return config;
    }

    /**
     * Writes a profile via a temporary file and an atomic rename.
     *
     * <p>Saving straight over the real file would truncate it first, so anything
     * reading it during the write — a rejoin, an admin command on an offline
     * player — could see an empty or half-written profile and take it for a
     * player with no progress. Renaming into place means a reader always sees
     * one whole version or the other, and a crash mid-save can't destroy a
     * profile either.
     */
    private void write(YamlConfiguration config, File file, UUID uuid) {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                // Some filesystems can't do it in one step; the copy is still
                // whole by the time it lands.
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save BoxCore profile for " + uuid, ex);
            if (temporary.exists() && !temporary.delete()) {
                plugin.getLogger().warning("Left a stray profile temp file at " + temporary);
            }
        }
    }

    /**
     * Blocks until every queued write has finished.
     *
     * <p>The write executor is single-threaded, so a task submitted now runs
     * strictly after everything already queued. Reads need that barrier: a
     * profile that was just unloaded still has its write in flight, and reading
     * around it would hand back stale data that the next save would then commit
     * over the top of the real thing.
     */
    private void awaitPendingWrites() {
        if (io.isShutdown()) {
            return;
        }
        try {
            io.submit(() -> {
            }).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Timed out waiting for pending BoxCore profile writes", ex);
        }
    }

    /** Snapshots on this thread, writes in the background. */
    public void save(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        // A clean profile is already on disk exactly as it stands; queueing a
        // second identical write only widens the window for a read to race it.
        if (profile == null || !profile.isDirty()) {
            return;
        }
        YamlConfiguration config = snapshot(profile);
        File file = fileFor(uuid);
        profile.markClean();
        io.execute(() -> write(config, file, uuid));
    }

    /** Writes the given (possibly detached) profile synchronously. */
    public void saveNow(PlayerProfile profile) {
        write(snapshot(profile), fileFor(profile.getUuid()), profile.getUuid());
        profile.markClean();
    }

    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    public void saveAllDirty() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null && profile.isDirty()) {
                save(uuid);
            }
        }
    }

    /** Synchronously saves everything cached and stops the IO thread (shutdown). */
    public void saveAllAndShutdown() {
        for (UUID uuid : new ArrayList<>(cache.keySet())) {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null) {
                saveNow(profile);
            }
        }
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public List<PlayerProfile> cached() {
        return new ArrayList<>(cache.values());
    }

    /** Deletes a player's stored profile and drops them from the cache. */
    public void delete(UUID uuid) {
        cache.remove(uuid);
        File file = fileFor(uuid);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete BoxCore profile file for " + uuid);
        }
    }
}
