package com.diamend.spyglass;

import java.io.File;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.diamend.spyglass.command.SpyCommand;
import com.diamend.spyglass.config.SpyglassConfig;
import com.diamend.spyglass.inspect.OnlineInspector;
import com.diamend.spyglass.offline.OfflineInspector;
import com.diamend.spyglass.offline.PlayerFiles;
import com.diamend.spyglass.report.DumpWriter;
import com.diamend.spyglass.report.Section;
import com.diamend.spyglass.util.Safe;
import com.diamend.spyglass.watch.WatchCategory;
import com.diamend.spyglass.watch.WatchListener;
import com.diamend.spyglass.watch.WatchManager;

/**
 * Spyglass: read any player's data from the server console.
 *
 * <p>Two halves, one command. For a player who is connected, every section is
 * read off the live server objects. For one who is not, the same sections are
 * read out of {@code playerdata/&lt;uuid&gt;.dat} and the stats and advancements
 * files beside it — so "any player" means any player, not any player who happens
 * to be logged in right now. On top of that, {@code /spy watch} tails what
 * someone is doing into the console as they do it.
 *
 * <p>Nothing here writes to a player. The one write the plugin ever asks for is
 * {@code Player#saveData()} before reading raw NBT, so what you read is current
 * rather than as old as the last autosave.
 */
public final class SpyglassPlugin extends JavaPlugin {

    private volatile SpyglassConfig settings;

    private PlayerFiles files;
    private OnlineInspector online;
    private OfflineInspector offline;
    private WatchManager watches;
    private DumpWriter dumps;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.settings = SpyglassConfig.load(getConfig());

        this.files = new PlayerFiles(getServer());
        this.online = new OnlineInspector(getServer());
        this.offline = new OfflineInspector();
        this.watches = new WatchManager(this, this::settings);
        this.dumps = buildDumpWriter();

        getServer().getPluginManager().registerEvents(
                new WatchListener(watches, this::settings, getServer()), this);

        SpyCommand command = new SpyCommand(this);
        if (getCommand("spy") != null) {
            getCommand("spy").setExecutor(command);
            getCommand("spy").setTabCompleter(command);
        }

        startConfiguredWatches();

        getLogger().info("Spyglass enabled with " + Section.values().length + " sections and "
                + WatchCategory.values().length + " watch categories"
                + (settings.autoWatch().isEmpty()
                        ? "" : ", auto-watching " + settings.autoWatch().size() + " player(s)")
                + ".");
    }

    @Override
    public void onDisable() {
        if (watches != null) {
            watches.clear();
        }
    }

    // ------------------------------------------------------------------
    // Parts
    // ------------------------------------------------------------------

    /** The current configuration; re-read by {@link #reloadSpyglass()}. */
    public SpyglassConfig settings() {
        return settings;
    }

    public PlayerFiles files() {
        return files;
    }

    public OnlineInspector online() {
        return online;
    }

    public OfflineInspector offline() {
        return offline;
    }

    public WatchManager watches() {
        return watches;
    }

    public DumpWriter dumps() {
        return dumps;
    }

    /** Re-reads config.yml. Watches in force are left alone. */
    public void reloadSpyglass() {
        reloadConfig();
        this.settings = SpyglassConfig.load(getConfig());
        this.dumps = buildDumpWriter();
        startConfiguredWatches();
        getLogger().info("Spyglass configuration reloaded.");
    }

    // ------------------------------------------------------------------
    // Threading
    // ------------------------------------------------------------------

    /**
     * Runs work that touches the disk off the main thread.
     *
     * <p>Reading a save file, a stats file or a folder listing has no business
     * on the server tick, so every offline read goes through here and comes back
     * via {@link #sync(Runnable)} to be sent.
     */
    public void async(Runnable task) {
        if (!isEnabled()) {
            return;
        }
        try {
            getServer().getScheduler().runTaskAsynchronously(this, () -> guard(task));
        } catch (RuntimeException ex) {
            // Bukkit refuses to schedule for a plugin that is going away. Do the
            // work here rather than not at all.
            guard(task);
        }
    }

    /** Runs work that has to be on the main thread, from wherever we are. */
    public void sync(Runnable task) {
        if (!isEnabled() || getServer().isPrimaryThread()) {
            guard(task);
            return;
        }
        try {
            getServer().getScheduler().runTask(this, () -> guard(task));
        } catch (RuntimeException ex) {
            guard(task);
        }
    }

    private void guard(Runnable task) {
        try {
            task.run();
        } catch (Throwable ex) {
            getLogger().warning("Spyglass task failed: " + ex);
        }
    }

    // ------------------------------------------------------------------
    // Setup helpers
    // ------------------------------------------------------------------

    private DumpWriter buildDumpWriter() {
        return new DumpWriter(new File(getDataFolder(), settings.dumpFolder()), settings.dumpKeep());
    }

    /**
     * Starts console watches for the names in {@code watch.auto} who are already
     * online — the rest are picked up as they join.
     */
    private void startConfiguredWatches() {
        if (settings.autoWatch().isEmpty()) {
            return;
        }
        Safe.run(() -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (settings.isAutoWatched(player.getName())) {
                    watches.add(getServer().getConsoleSender(), player.getUniqueId(),
                            player.getName(), settings.defaultCategories());
                }
            }
        });
    }
}
