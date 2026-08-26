package com.diamend.customachievements.api;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.achievement.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The entry point for other plugins.
 *
 * <p>Java plugins can call these directly (soft-depend on
 * {@code CustomAchievements} and guard with {@link #isAvailable()}); everything
 * else — Skript, command blocks, datapacks, another plugin's reward-command
 * list — should run {@code /ca trigger <player> <key> [amount]} from the
 * console instead, which does exactly the same thing.
 *
 * <p>The key is free text you invent. It matches a {@code CUSTOM} objective
 * whose target is that key, case-insensitively.
 */
public final class CustomAchievementsAPI {

    private CustomAchievementsAPI() {
    }

    /** Whether CustomAchievements is installed and enabled on this server. */
    public static boolean isAvailable() {
        return plugin() != null;
    }

    /** Fires a custom trigger key once. */
    public static void trigger(Player player, String key) {
        trigger(player, key, 1);
    }

    /**
     * Adds {@code amount} to every {@code CUSTOM} objective listening for this
     * key, awarding any achievement that finishes. Does nothing if the plugin
     * isn't loaded, so callers don't have to guard every call.
     */
    public static void trigger(Player player, String key, int amount) {
        CustomAchievementsPlugin plugin = plugin();
        if (plugin == null || player == null || key == null || key.isBlank() || amount <= 0) {
            return;
        }
        plugin.getAchievementService().handleCustom(player, key, amount);
    }

    /**
     * Sets matching objectives to an absolute value rather than adding to them,
     * for callers that already track their own running total. Progress never
     * exceeds what the objective requires.
     */
    public static void set(Player player, String key, int value) {
        CustomAchievementsPlugin plugin = plugin();
        if (plugin == null || player == null || key == null || key.isBlank() || value < 0) {
            return;
        }
        plugin.getAchievementService().setCustom(player, key, value);
    }

    /** Whether the player has already unlocked an achievement, by its id. */
    public static boolean hasCompleted(Player player, String achievementId) {
        CustomAchievementsPlugin plugin = plugin();
        if (plugin == null || player == null || achievementId == null) {
            return false;
        }
        return plugin.getPlayerDataManager().get(player.getUniqueId()).isCompleted(achievementId);
    }

    /** Grants a whole achievement outright. Returns false if they already had it. */
    public static boolean grant(Player player, String achievementId) {
        CustomAchievementsPlugin plugin = plugin();
        if (plugin == null || player == null || achievementId == null) {
            return false;
        }
        Achievement achievement = plugin.getAchievementManager().get(achievementId);
        return achievement != null && plugin.getAchievementService().grant(player, achievement);
    }

    private static CustomAchievementsPlugin plugin() {
        Plugin found = Bukkit.getPluginManager().getPlugin("CustomAchievements");
        return found instanceof CustomAchievementsPlugin self && self.isEnabled() ? self : null;
    }
}
