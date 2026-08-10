package com.diamend.customachievements;

import com.diamend.customachievements.achievement.AchievementManager;
import com.diamend.customachievements.achievement.AchievementService;
import com.diamend.customachievements.achievement.TriggerType;
import com.diamend.customachievements.command.AchievementsCommand;
import com.diamend.customachievements.data.PlayerDataManager;
import com.diamend.customachievements.gui.ChatInputManager;
import com.diamend.customachievements.gui.Menu;
import com.diamend.customachievements.listener.AchievementTriggerListener;
import com.diamend.customachievements.listener.ConnectionListener;
import com.diamend.customachievements.listener.GuiListener;
import com.diamend.customachievements.listener.MythicMobsHook;
import com.diamend.customachievements.listener.WorldTriggerListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // The last CustomAchievements menu each player had open, so /reopen can
    // restore it (e.g. an editor closed by accident) without losing state.
    private final Map<UUID, Menu> lastMenu = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        setupConfig();

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

    /**
     * Writes the bundled config on first run and, on upgrades, merges any new
     * options introduced by this version into the server owner's existing
     * config.yml. Only missing keys are added — existing values (and the file's
     * comments) are left untouched — so newly-added options no longer sit silently
     * at their code defaults after an update.
     */
    private void setupConfig() {
        saveDefaultConfig();
        reloadConfig();
        org.bukkit.configuration.file.FileConfiguration config = getConfig();
        java.io.InputStream defaultsStream = getResource("config.yml");
        if (defaultsStream == null) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration defaults =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defaultsStream, java.nio.charset.StandardCharsets.UTF_8));
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue; // sections are recreated implicitly by their leaf keys
            }
            if (!config.contains(key)) {
                config.set(key, defaults.get(key));
                added++;
            }
        }
        if (added > 0) {
            saveConfig();
            getLogger().info("Added " + added + " new option(s) to config.yml from this version's defaults.");
        }
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
        int ticks;
        try {
            ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        } catch (RuntimeException ex) {
            // The play-time statistic isn't readable on every server
            // implementation (notably the mock server used in unit tests); when
            // it can't be read we skip the sync rather than breaking the join.
            return;
        }
        int hours = ticks / 72000; // 20 ticks * 3600s
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
        // Standalone /reopen command shares the same handler.
        PluginCommand reopen = getCommand("careopen");
        if (reopen != null) {
            reopen.setExecutor(handler);
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

    /**
     * Whether secret (hidden) achievements reveal their name and a one-line hint
     * in the menu before they're unlocked. When false they show as a bare {@code ???}.
     */
    public boolean isSecretHintsEnabled() {
        return getConfig().getBoolean("secret-show-hints", true);
    }

    /**
     * How many description lines a secret achievement's hint reveals: {@code -1}
     * for the whole description, {@code 0} for none (name only), or a positive
     * number to cap it at the first N lines.
     */
    public int getSecretHintLines() {
        return getConfig().getInt("secret-hint-lines", -1);
    }

    // ------------------------------------------------------------------
    // Last-opened menu tracking (for /reopen).
    // ------------------------------------------------------------------

    /** Records the menu a player just opened so {@code /reopen} can restore it. */
    public void setLastMenu(UUID uuid, Menu menu) {
        lastMenu.put(uuid, menu);
    }

    /** The last CustomAchievements menu a player had open, or null. */
    public Menu getLastMenu(UUID uuid) {
        return lastMenu.get(uuid);
    }

    public void clearLastMenu(UUID uuid) {
        lastMenu.remove(uuid);
    }
}
