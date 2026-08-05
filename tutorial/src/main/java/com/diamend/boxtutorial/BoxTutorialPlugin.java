package com.diamend.boxtutorial;

import com.diamend.boxtutorial.command.TutorialCommand;
import com.diamend.boxtutorial.data.ProgressStore;
import com.diamend.boxtutorial.gui.GuiListener;
import com.diamend.boxtutorial.guide.TutorialManager;
import com.diamend.boxtutorial.guide.TutorialService;
import com.diamend.boxtutorial.listener.ConnectionListener;
import com.diamend.boxtutorial.listener.StepListener;
import com.diamend.boxtutorial.ui.GuideBar;
import com.diamend.boxtutorial.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * BoxTutorial — the first ten minutes on a boxpvp server, explained.
 *
 * <p>Deliberately small and standalone. It knows nothing about BoxCore, an
 * economy plugin or a warp plugin: what a step asks for is a line of
 * {@code tutorial.yml}, and what happens when it's done is a command the server
 * owner chose. That is the whole plugin — a config-driven sequence, a boss bar
 * and a checklist.
 */
public class BoxTutorialPlugin extends JavaPlugin {

    private Messages messages;
    private ProgressStore store;
    private TutorialManager tutorial;
    private TutorialService service;
    private GuideBar guide;

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

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new StepListener(this), this);
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

        getLogger().info("BoxTutorial enabled with " + tutorial.stepCount() + " step(s) and "
                + tutorial.topics().size() + " topic(s).");
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
}
