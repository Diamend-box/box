package com.diamend.robobear.listener;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Loads and saves player records, and ends runs that shouldn't survive.
 *
 * <p>Both endings are configurable but both default to on, because both would
 * otherwise be free ways to stop the clock: dying out of a losing run and
 * logging out of a losing run are the same move.
 */
public class ConnectionListener implements Listener {

    private final RoboBearPlugin plugin;

    public ConnectionListener(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.data().loadAsync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getBoolean("run.fail-on-quit", true)) {
            plugin.service().abandon(player.getUniqueId());
        }
        plugin.prompts().clear(player.getUniqueId());
        plugin.data().unloadAsync(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("run.fail-on-death", true)) {
            return;
        }
        Player player = event.getEntity();
        if (plugin.service().isRunning(player)) {
            plugin.service().fail(player, "failed-death");
        }
    }
}
