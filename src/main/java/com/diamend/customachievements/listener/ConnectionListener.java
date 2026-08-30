package com.diamend.customachievements.listener;

import com.diamend.customachievements.CustomAchievementsPlugin;
import com.diamend.customachievements.data.PlayerData;
import com.diamend.customachievements.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads player achievement data on join and persists it on quit, clears any
 * pending editor chat prompt / remembered menu when a player disconnects, and
 * reminds returning players about unclaimed reward items.
 */
public class ConnectionListener implements Listener {

    private final CustomAchievementsPlugin plugin;

    public ConnectionListener(CustomAchievementsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerDataManager().load(player.getUniqueId());
        plugin.syncPlaytime(player);
        plugin.syncAuraSkills(player);
        // Credit anything they'd already done before an achievement existed.
        plugin.getAchievementService().backfill(player);
        // Count what they've already unlocked toward any "unlock N achievements"
        // objective. Not part of the statistics backfill: this is read from the
        // player's own data, so it costs nothing and can never go stale.
        plugin.getAchievementService().handleUnlockCount(player);
        notifyPendingRewards(player);
    }

    private void notifyPendingRewards(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getCached(player.getUniqueId());
        if (data == null || !data.hasPendingRewards()) {
            return;
        }
        int count = data.getPendingRewards().size();
        // Slight delay so the message lands after the join/MOTD spam.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(Text.parse("<gold>You have <yellow>" + count
                        + "<gold> unclaimed reward item(s). Use <white>/ca claim<gold> to collect them."));
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInput().cancel(event.getPlayer().getUniqueId());
        plugin.clearLastMenu(event.getPlayer().getUniqueId());
        plugin.getAchievementService().forgetPlayer(event.getPlayer().getUniqueId());
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }
}
