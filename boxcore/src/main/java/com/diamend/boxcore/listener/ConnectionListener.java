package com.diamend.boxcore.listener;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.data.PlayerProfile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Core profile lifecycle: load on join, save and unload on quit.
 *
 * <p>Registered before any module's listener so a module reacting to a join
 * always finds the profile already in memory.
 */
public class ConnectionListener implements Listener {

    private final BoxCorePlugin plugin;

    public ConnectionListener(BoxCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        boolean firstTime = plugin.profiles().isNew(uuid);
        plugin.profiles().load(uuid);

        PlayerProfile profile = plugin.profiles().get(uuid);
        profile.setName(event.getPlayer().getName());

        if (firstTime) {
            int starting = plugin.getConfig().getInt("skills.starting-points", 0);
            if (starting > 0) {
                profile.addPoints(starting);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.profiles().unload(event.getPlayer().getUniqueId());
    }
}
