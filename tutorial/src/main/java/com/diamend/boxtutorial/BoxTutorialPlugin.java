package com.diamend.boxtutorial;

import com.diamend.boxtutorial.arena.ArenaBlueprint;
import com.diamend.boxtutorial.arena.InstanceManager;
import com.diamend.boxtutorial.command.TutorialCommand;
import com.diamend.boxtutorial.data.ProgressStore;
import com.diamend.boxtutorial.gui.GuiListener;
import com.diamend.boxtutorial.guide.TutorialManager;
import com.diamend.boxtutorial.guide.TutorialService;
import com.diamend.boxtutorial.listener.ArenaListener;
import com.diamend.boxtutorial.listener.ConnectionListener;
import com.diamend.boxtutorial.listener.StepListener;
import com.diamend.boxtutorial.ui.GuideBar;
import com.diamend.boxtutorial.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * BoxTutorial — a first-run sandbox for people who've never played boxpvp.
 *
 * <p>{@code /tutorial} puts a player in their own copy of a small arena: a mine
 * that fills itself back up, and a villager who trades ore for gear. They're
 * walked through the loop the gamemode is made of — mine, sell, gear up — and
 * then sent back to spawn with what they made.
 *
 * <p>Standalone by design. It knows nothing about BoxCore, an economy plugin or
 * a warp plugin: the arena, the mines and the trades are all config, and the
 * only currency is the ore in the player's hands.
 */
public class BoxTutorialPlugin extends JavaPlugin {

    private Messages messages;
    private ProgressStore store;
    private TutorialManager tutorial;
    private TutorialService service;
    private GuideBar guide;
    private ArenaBlueprint blueprint;
    private InstanceManager instances;

    private BukkitTask autosaveTask;

    // Non-null only when PlaceholderAPI is installed and the expansion registered.
    private com.diamend.boxtutorial.integration.TutorialPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messages = new Messages(this);
        this.store = new ProgressStore(this);
        this.tutorial = new TutorialManager(this);
        this.service = new TutorialService(this);
        this.guide = new GuideBar(this);
        this.blueprint = new ArenaBlueprint(this);
        this.instances = new InstanceManager(this, blueprint);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new StepListener(this), this);
        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        registerCommand();
        registerPlaceholders();
        guide.start();
        startAutosave();

        // Pick up anyone already online (a /reload, or a hot install).
        for (Player player : getServer().getOnlinePlayers()) {
            store.get(player.getUniqueId()).setName(player.getName());
            guide.refresh(player);
        }

        getLogger().info("BoxTutorial enabled with " + tutorial.stepCount() + " step(s), "
                + tutorial.topics().size() + " topic(s), " + blueprint.mines().size()
                + " mine(s) and " + blueprint.trades().size() + " trade(s).");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            try {
                placeholders.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI may already be gone during shutdown.
            }
            placeholders = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (service != null) {
            // Nobody gets logged out inside a world whose plugin has gone.
            service.evacuate();
        }
        if (guide != null) {
            guide.stop();
        }
        if (store != null) {
            store.saveAndShutdown();
        }
        getLogger().info("BoxTutorial disabled.");
    }

    /** Re-reads config.yml and tutorial.yml, and restarts the on-screen guide. */
    public void reloadEverything() {
        reloadConfig();
        tutorial.load();
        // Arenas are built from the blueprint at claim time, so the players
        // already inside one keep the room they're standing in; the next claim
        // uses the new numbers.
        blueprint.load();
        guide.start();
        for (Player player : getServer().getOnlinePlayers()) {
            guide.refresh(player);
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("tutorial");
        if (command == null) {
            getLogger().warning("Command 'tutorial' is missing from plugin.yml!");
            return;
        }
        TutorialCommand handler = new TutorialCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    /**
     * Registers the PlaceholderAPI expansion when PlaceholderAPI is installed.
     * The expansion class imports PlaceholderAPI types, so it is only touched
     * behind the plugin-enabled check — that keeps the dependency genuinely soft.
     */
    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            com.diamend.boxtutorial.integration.TutorialPlaceholders expansion =
                    new com.diamend.boxtutorial.integration.TutorialPlaceholders(this);
            if (expansion.register()) {
                this.placeholders = expansion;
                getLogger().info("Registered PlaceholderAPI expansion (%boxtutorial_...%).");
            }
        } catch (Throwable ex) {
            this.placeholders = null;
            getLogger().warning("PlaceholderAPI is present but the expansion failed to register: "
                    + ex.getMessage());
        }
    }

    private void startAutosave() {
        int minutes = getConfig().getInt("autosave-minutes", 5);
        if (minutes <= 0) {
            return;
        }
        long ticks = minutes * 60L * 20L;
        autosaveTask = getServer().getScheduler()
                .runTaskTimer(this, () -> store.saveIfDirty(), ticks, ticks);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Messages messages() {
        return messages;
    }

    public ProgressStore store() {
        return store;
    }

    public TutorialManager tutorial() {
        return tutorial;
    }

    public TutorialService service() {
        return service;
    }

    public GuideBar guide() {
        return guide;
    }

    public ArenaBlueprint blueprint() {
        return blueprint;
    }

    public InstanceManager instances() {
        return instances;
    }
}
