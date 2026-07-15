package com.diamend.customachievements.listener;

import com.diamend.customachievements.CustomAchievementsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads player achievement data on join and persists it on quit, and clears any
 * pending editor chat prompt when a player disconnects.
 */
public class ConnectionListener implements Listener {

    private final CustomAchievementsPlugin plugin;

    public ConnectionListener(CustomAchievementsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChatInput().cancel(event.getPlayer().getUniqueId());
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }
}
