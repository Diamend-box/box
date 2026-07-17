package com.diamend.customachievements;

import com.diamend.customachievements.achievement.AchievementManager;
import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.command.AchievementsCommand;
import com.diamend.customachievements.data.PlayerDataManager;
import com.diamend.customachievements.gui.ChatInputManager;
import com.diamend.customachievements.listener.AchievementTriggerListener;
import com.diamend.customachievements.listener.ConnectionListener;
import com.diamend.customachievements.listener.GuiListener;
import com.diamend.customachievements.listener.MythicMobsHook;
import com.diamend.customachievements.listener.WorldTriggerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Entry point for the CustomAchievements plugin.
 */
public class CustomAchievementsPlugin extends JavaPlugin {

    private AchievementManager achievementManager;
    private PlayerDataManager playerDataManager;
    private AchievementService achievementService;
    private ChatInputManager chatInput;

    // Non-null only when AuraSkills is installed.
    private com.diamend.customachievements.listener.AuraSkillsListener auraSkills;

    // Non-null only when PlaceholderAPI is installed and the expansion registered.
    private com.diamend.customachievements.integration.AchievementsPlaceholders placeholders;

    private BukkitTask playtimeTask;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.achievementManager = new AchievementManager(this);
        this.achievementManager.load();

        this.playerDataManager = new PlayerDataManager(this);
        this.achievementService = new AchievementService(this, achievementManager, playerDataManager);
        this.chatInput = new ChatInputManager();

        registerListeners();
        registerCommand();
        registerPlaceholders();
        startTasks();

        // Load data for anyone already online (e.g. after a /reload).
        for (Player player : getServer().getOnlinePlayers()) {
            playerDataManager.load(player.getUniqueId());
        }

        getLogger().info("CustomAchievements enabled.");
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
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAllAndShutdown();
        }
        getLogger().info("CustomAchievements disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new AchievementTriggerListener(this, achievementService), this);
        getServer().getPluginManager().registerEvents(new WorldTriggerListener(achievementService), this);
        MythicMobsHook.register(this, achievementService);

        // AuraSkills integration is loaded reflectively-safe: the listener class
        // (which imports AuraSkills types) is only referenced when the plugin is
        // present, so we don't hard-depend on it.
        if (getServer().getPluginManager().isPluginEnabled("AuraSkills")) {
            try {
                this.auraSkills = new com.diamend.customachievements.listener.AuraSkillsListener(achievementService);
                getServer().getPluginManager().registerEvents(auraSkills, this);
                getLogger().info("Hooked into AuraSkills.");
            } catch (Throwable ex) {
                this.auraSkills = null;
                getLogger().warning("AuraSkills is present but the hook failed to load: " + ex.getMessage());
            }
        }
    }

    /**
     * Registers the PlaceholderAPI expansion when PlaceholderAPI is present. The
     * expansion class imports PlaceholderAPI types, so it is only touched behind
     * the plugin-enabled check (soft dependency), guarded like the AuraSkills hook.
     */
    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            this.placeholders = new com.diamend.customachievements.integration.AchievementsPlaceholders(this);
            if (placeholders.register()) {
                getLogger().info("Registered PlaceholderAPI expansion (%customachievements_...%).");
            }
        } catch (Throwable ex) {
            this.placeholders = null;
            getLogger().warning("PlaceholderAPI is present but the expansion failed to register: " + ex.getMessage());
        }
    }

    /** Reads a player's total playtime (hours) and updates gauge objectives. */
    public void syncPlaytime(Player player) {
        int hours = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 72000; // 20 ticks * 3600s
        achievementService.handleGauge(player, TriggerType.PLAYTIME_HOURS, null, hours);
    }

    /** If AuraSkills is present, syncs the player's current skill levels shortly after join. */
    public void syncAuraSkills(Player player) {
        if (auraSkills == null) {
            return;
        }
        // Delay so AuraSkills has finished loading the player's user data.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                try {
                    auraSkills.syncLevels(player);
                } catch (Throwable ignored) {
                    // AuraSkills not ready / user missing — level-up events still cover it.
                }
            }
        }, 40L);
    }

    private void registerCommand() {
        AchievementsCommand handler = new AchievementsCommand(this);
        PluginCommand command = getCommand("achievements");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'achievements' is missing from plugin.yml!");
        }
    }

    private void startTasks() {
        if (getConfig().getBoolean("playtime-tracking", true)) {
            // Every minute, refresh each online player's playtime-hours gauge
            // from the server's persisted PLAY_ONE_MINUTE statistic.
            playtimeTask = getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : getServer().getOnlinePlayers()) {
                    syncPlaytime(player);
                }
            }, 1200L, 1200L);
        }

        int autosaveMinutes = getConfig().getInt("autosave-minutes", 5);
        if (autosaveMinutes > 0) {
            long ticks = autosaveMinutes * 60L * 20L;
            autosaveTask = getServer().getScheduler().runTaskTimer(this,
                    () -> playerDataManager.saveAllDirty(), ticks, ticks);
        }
    }

    // ------------------------------------------------------------------
    // Accessors used by the GUIs and commands.
    // ------------------------------------------------------------------

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public AchievementService getAchievementService() {
        return achievementService;
    }

    public ChatInputManager getChatInput() {
        return chatInput;
    }

    /**
     * Whether editor text prompts should use the off-chat anvil GUI. When false
     * (or when the anvil can't be built on this server build) the editor falls
     * back to typing in chat.
     */
    public boolean isAnvilInputEnabled() {
        return getConfig().getBoolean("use-anvil-input", true);
    }
}
