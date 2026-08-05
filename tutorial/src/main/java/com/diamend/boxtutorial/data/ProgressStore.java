package com.diamend.boxtutorial.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Everyone's progress, in one small {@code progress.yml}.
 *
 * <p>One file rather than one per player: a tutorial record is a few dozen bytes
 * and is read exactly once, on join, for every player who has ever logged in.
 * Splitting that across thousands of tiny files would buy nothing.
 *
 * <p>Saving renders the file on the calling (main) thread and writes it on a
 * background thread, so a save can't stall a tick, and writes can't race each
 * other. Shutdown waits for the write it just queued.
 */
public class ProgressStore {

    /**
     * Path separator for the stored file. Step ids are config keys and a server
     * owner is entitled to call one {@code shop.buy}; the default dot separator
     * would quietly turn that into two nested keys and read back as nothing.
     */
    private static final char SEPARATOR = '/';

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Progress> cache = new ConcurrentHashMap<>();

    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "BoxTutorial-IO");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean dirty;

    public ProgressStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "progress.yml");
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create the BoxTutorial data folder.");
        }
        load();
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    private void load() {
        cache.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator(SEPARATOR);
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read progress.yml", ex);
            return;
        }
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping progress entry '" + key + "': not a UUID.");
                continue;
            }
            ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            Progress progress = new Progress(uuid);
            progress.setName(entry.getString("name", ""));
            progress.setStarted(entry.getBoolean("started", false));
            progress.setFinished(entry.getBoolean("finished", false));
            progress.setStopped(entry.getBoolean("stopped", false));
            progress.setPlayedMillis(entry.getLong("played-millis", 0L));
            for (String step : entry.getStringList("completed")) {
                progress.complete(step);
            }
            for (String step : entry.getStringList("skipped")) {
                progress.markSkipped(step);
            }
            ConfigurationSection counts = entry.getConfigurationSection("counts");
            if (counts != null) {
                for (String step : counts.getKeys(false)) {
                    progress.setCount(step, counts.getInt(step, 0));
                }
            }
            cache.put(uuid, progress);
        }
    }

    /** True when this player already has a record — i.e. we've seen them before. */
    public boolean known(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** The player's record, creating an empty one the first time they're asked for. */
    public Progress get(UUID uuid) {
        return cache.computeIfAbsent(uuid, Progress::new);
    }

    public Collection<Progress> all() {
        return Collections.unmodifiableCollection(cache.values());
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
        dirty = true;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Marks the store as having unsaved changes. */
    public void touch() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    /** Renders now, writes in the background. */
    public void save() {
        String rendered = render();
        dirty = false;
        try {
            io.execute(() -> write(rendered));
        } catch (RejectedExecutionException ex) {
            write(rendered); // shutting down; the write still has to happen
        }
    }

    /** Final save, on the calling thread, then stops the writer. */
    public void saveAndShutdown() {
        String rendered = render();
        dirty = false;
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        write(rendered);
    }

    private String render() {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator(SEPARATOR);
        for (Progress progress : cache.values()) {
            String base = "players" + SEPARATOR + progress.uuid() + SEPARATOR;
            config.set(base + "name", progress.name());
            config.set(base + "started", progress.started());
            config.set(base + "finished", progress.finished());
            config.set(base + "stopped", progress.stopped());
            config.set(base + "played-millis", progress.playedMillis());
            config.set(base + "completed", new java.util.ArrayList<>(progress.completed()));
            config.set(base + "skipped", new java.util.ArrayList<>(progress.skipped()));
            for (Map.Entry<String, Integer> count : progress.counts().entrySet()) {
                config.set(base + "counts" + SEPARATOR + count.getKey(), count.getValue());
            }
        }
        return config.saveToString();
    }

    private void write(String contents) {
        try {
            Path target = file.toPath();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = target.resolveSibling(file.getName() + ".tmp");
            Files.writeString(temp, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write progress.yml", ex);
        }
    }
}
