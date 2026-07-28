package com.diamend.boxcore;

import com.diamend.boxcore.boost.BoostsModule;
import com.diamend.boxcore.collection.CollectionsModule;
import com.diamend.boxcore.command.BoxCommand;
import com.diamend.boxcore.data.PlayerProfile;
import com.diamend.boxcore.data.ProfileManager;
import com.diamend.boxcore.gui.GuiListener;
import com.diamend.boxcore.listener.ConnectionListener;
import com.diamend.boxcore.module.BoxModule;
import com.diamend.boxcore.module.ModuleManager;
import com.diamend.boxcore.ore.CompressorModule;
import com.diamend.boxcore.ore.OreValues;
import com.diamend.boxcore.playtime.PlaytimeModule;
import com.diamend.boxcore.skill.SkillsModule;
import com.diamend.boxcore.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Entry point for BoxCore — the Box server's utility and progression core.
 *
 * <p>The plugin itself owns only the shared plumbing: player profiles, chat
 * messages, the {@code /box} command and the module registry. Every actual
 * feature is a {@link BoxModule}, so adding the next utility means writing one
 * class and registering it here.
 */
public class BoxCorePlugin extends JavaPlugin {

    private ProfileManager profiles;
    private Messages messages;
    private ModuleManager modules;
    private OreValues ores;

    private SkillsModule skillsModule;
    private CollectionsModule collectionsModule;
    private PlaytimeModule playtimeModule;
    private CompressorModule compressorModule;
    private BoostsModule boostsModule;

    private BukkitTask autosaveTask;

    // Non-null only when PlaceholderAPI is installed and the expansion registered.
    private com.diamend.boxcore.integration.BoxPlaceholders placeholders;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.profiles = new ProfileManager(this);
        this.messages = new Messages(this);
        this.modules = new ModuleManager(this);
        this.ores = new OreValues(this);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        this.skillsModule = new SkillsModule(this);
        this.collectionsModule = new CollectionsModule(this);
        this.playtimeModule = new PlaytimeModule(this);
        this.compressorModule = new CompressorModule(this);
        this.boostsModule = new BoostsModule(this);
        modules.register(skillsModule);
        modules.register(collectionsModule);
        modules.register(playtimeModule);
        modules.register(compressorModule);
        modules.register(boostsModule);
        modules.enableAll();

        registerCommand();
        registerPlaceholders();
        startAutosave();

        // Pick up anyone already online (a /reload, or a hot install).
        for (Player player : getServer().getOnlinePlayers()) {
            profiles.load(player.getUniqueId());
            profiles.get(player.getUniqueId()).setName(player.getName());
        }

        getLogger().info("BoxCore enabled with " + modules.activeModules().size()
                + " of " + modules.registered().size() + " module(s) active.");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            try {
                placeholders.unregister();
            } catch (Throwable ignored) {
                // PlaceholderAPI may already be gone during shutdown.
            }
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (profiles != null) {
            profiles.saveAllAndShutdown();
        }
        getLogger().info("BoxCore disabled.");
    }

    private void registerCommand() {
        BoxCommand handler = new BoxCommand(this);
        PluginCommand command = getCommand("box");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'box' is missing from plugin.yml!");
        }
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
            com.diamend.boxcore.integration.BoxPlaceholders expansion =
                    new com.diamend.boxcore.integration.BoxPlaceholders(this);
            if (expansion.register()) {
                this.placeholders = expansion;
                getLogger().info("Registered PlaceholderAPI expansion (%boxcore_...%).");
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
        autosaveTask = getServer().getScheduler().runTaskTimer(this,
                () -> profiles.saveAllDirty(), ticks, ticks);
    }

    /**
     * Awards skill points and tells the player where they came from. Every
     * point source in the plugin funnels through here.
     */
    public void grantPoints(Player player, int amount, String source) {
        if (player == null || amount <= 0) {
            return;
        }
        PlayerProfile profile = profiles.get(player.getUniqueId());
        profile.addPoints(amount);
        messages.send(player, "point-earned",
                "amount", amount,
                "plural", Messages.plural(amount),
                "source", source);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public ProfileManager profiles() {
        return profiles;
    }

    public Messages messages() {
        return messages;
    }

    public ModuleManager modules() {
        return modules;
    }

    public SkillsModule skills() {
        return modules.isActive("skills") ? skillsModule : null;
    }

    public CollectionsModule collections() {
        return modules.isActive("collections") ? collectionsModule : null;
    }

    public PlaytimeModule playtime() {
        return modules.isActive("playtime") ? playtimeModule : null;
    }

    public CompressorModule compressor() {
        return modules.isActive("compressor") ? compressorModule : null;
    }

    public BoostsModule boosts() {
        return modules.isActive("boosts") ? boostsModule : null;
    }

    /**
     * The shared ore-value accessor. Lives on the plugin rather than inside the
     * compressor module because everything that counts ore reads it, and a
     * disabled module must not take the counting path down with it.
     */
    public OreValues ores() {
        return ores;
    }
}
