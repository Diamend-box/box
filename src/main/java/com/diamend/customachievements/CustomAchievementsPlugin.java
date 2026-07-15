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
        startTasks();

        // Load data for anyone already online (e.g. after a /reload).
        for (Player player : getServer().getOnlinePlayers()) {
            playerDataManager.load(player.getUniqueId());
        }

        getLogger().info("CustomAchievements enabled.");
    }

    @Override
    public void onDisable() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        getLogger().info("CustomAchievements disabled.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new AchievementTriggerListener(achievementService), this);
        getServer().getPluginManager().registerEvents(new WorldTriggerListener(achievementService), this);
        MythicMobsHook.register(this, achievementService);
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
            // Every minute (1200 ticks), grant a minute of playtime progress.
            playtimeTask = getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : getServer().getOnlinePlayers()) {
                    achievementService.handle(player, TriggerType.PLAYTIME_MINUTES, (String) null, 1);
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
}
