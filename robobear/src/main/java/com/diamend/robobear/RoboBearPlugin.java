package com.diamend.robobear;

import com.diamend.robobear.challenge.MilestoneRewards;
import com.diamend.robobear.challenge.RoboService;
import com.diamend.robobear.command.RoboBearCommand;
import com.diamend.robobear.data.DataManager;
import com.diamend.robobear.gui.GuiListener;
import com.diamend.robobear.listener.ConnectionListener;
import com.diamend.robobear.listener.MiningListener;
import com.diamend.robobear.mine.MineIndex;
import com.diamend.robobear.mine.MineRegion;
import com.diamend.robobear.util.ChatPrompt;
import com.diamend.robobear.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Entry point for RoboBear — a Bee Swarm Simulator style challenge ladder,
 * rebuilt on top of the mines a boxpvp server already has.
 *
 * <p>The shape is the game's: a pass buys entry and a handful of Cogs, each
 * round offers a choice of jobs under one clock, finishing pays Cogs to spend on
 * upgrades before the next round, and clearing a milestone round pays out for
 * keeps. The difference is what a job is — the fields are your mines and the
 * pollen is whatever comes out of them.
 *
 * <p>It suits this server for a reason beyond theme. A run is a voluntary way to
 * turn the risk dial up: you commit to a clock, you can't break off to bank in
 * the middle, and a payout lands on the floor where anyone can see it.
 */
public class RoboBearPlugin extends JavaPlugin {

    private Messages messages;
    private ChatPrompt prompts;
    private MineIndex mines;
    private MilestoneRewards milestones;
    private RoboService service;
    private DataManager data;

    private BukkitTask tickTask;
    private BukkitTask refreshTask;
    private BukkitTask autosaveTask;

    // Non-null only when PlaceholderAPI is installed and the expansion registered.
    private com.diamend.robobear.integration.RoboBearPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messages = new Messages(this);
        this.prompts = new ChatPrompt(this);
        this.data = new DataManager(this);
        this.mines = new MineIndex(this);
        this.milestones = new MilestoneRewards(this);
        this.service = new RoboService(this);

        mines.refresh();
        logMines();

        milestones.load();
        milestones.installPlaceholdersIfEmpty();

        getServer().getPluginManager().registerEvents(new MiningListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand command = getCommand("robobear");
        if (command != null) {
            RoboBearCommand executor = new RoboBearCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("The 'robobear' command is missing from plugin.yml; "
                    + "nothing will be able to open the menu.");
        }

        startTasks();
        registerPlaceholders();

        // Players are already online across a /reload.
        for (Player player : Bukkit.getOnlinePlayers()) {
            data.loadAsync(player);
        }

        getLogger().info("RoboBear enabled with " + milestones.size() + " milestone tier(s) over "
                + mines.size() + " mine(s) from '" + mines.activeSource() + "'.");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (service != null) {
            service.abandonAll();
        }
        if (data != null) {
            data.saveAllBlocking();
        }
        if (placeholders != null) {
            placeholders.unregisterSafely();
            placeholders = null;
        }
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private void startTasks() {
        // The run clock. Once a second is as precise as a countdown needs to be.
        tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> service.tick(), 20L, 20L);

        long refresh = getConfig().getLong("mines.refresh-seconds", 300L);
        if (refresh > 0) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(this,
                    () -> mines.refresh(), refresh * 20L, refresh * 20L);
        }

        long autosave = getConfig().getLong("storage.autosave-minutes", 5L);
        if (autosave > 0) {
            autosaveTask = Bukkit.getScheduler().runTaskTimer(this,
                    () -> data.saveAllAsync(), autosave * 60L * 20L, autosave * 60L * 20L);
        }
    }

    private void cancelTasks() {
        for (BukkitTask task : new BukkitTask[] { tickTask, refreshTask, autosaveTask }) {
            if (task != null) {
                task.cancel();
            }
        }
        tickTask = null;
        refreshTask = null;
        autosaveTask = null;
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            com.diamend.robobear.integration.RoboBearPlaceholders expansion =
                    new com.diamend.robobear.integration.RoboBearPlaceholders(this);
            if (expansion.register()) {
                this.placeholders = expansion;
                getLogger().info("Registered the PlaceholderAPI expansion.");
            }
        } catch (Throwable error) {
            getLogger().warning("PlaceholderAPI is installed but the expansion could not be "
                    + "registered: " + error);
        }
    }

    private void logMines() {
        if (!getConfig().getBoolean("mines.log-on-startup", true)) {
            return;
        }
        if (mines.size() == 0) {
            getLogger().warning("No mines were found from source '" + mines.activeSource()
                    + "'. Mining objectives can't be built until there are some — either install "
                    + "MineResetLite, or set mines.source to 'manual' and build regions with "
                    + "/rb pos1, /rb pos2 and /rb mine set <id>.");
            return;
        }
        getLogger().info("Mines from '" + mines.activeSource() + "':");
        for (MineRegion mine : mines.all()) {
            getLogger().info("  " + mine.id() + " — " + mine.boundsDescription()
                    + " (" + mine.volume() + " blocks)");
        }
    }

    /** Re-reads every config file. Runs in flight are ended rather than orphaned. */
    public void reloadEverything() {
        reloadConfig();
        messages.reload();
        mines.manualProvider().load();
        mines.refresh();
        milestones.load();
        service.abandonAll();
        cancelTasks();
        startTasks();
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Messages messages() {
        return messages;
    }

    public ChatPrompt prompts() {
        return prompts;
    }

    public MineIndex mines() {
        return mines;
    }

    public MilestoneRewards milestones() {
        return milestones;
    }

    public RoboService service() {
        return service;
    }

    public DataManager data() {
        return data;
    }
}
