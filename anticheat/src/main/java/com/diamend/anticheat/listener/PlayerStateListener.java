package com.diamend.anticheat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.player.PlayerDataManager;

/**
 * Maintains {@link PlayerData} lifecycle and records the movement events that
 * grant a grace period: joins, teleports and respawns. During those windows the
 * {@link com.diamend.anticheat.exempt.ExemptionManager} skips combat checks so
 * the position/velocity discontinuity they cause never reads as cheating.
 */
public final class PlayerStateListener implements Listener {

    private final PlayerDataManager players;
    private final AlertManager alerts;

    public PlayerStateListener(PlayerDataManager players, AlertManager alerts) {
        this.players = players;
        this.alerts = alerts;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        players.create(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
        alerts.clearVerbose(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        markTeleport(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        markTeleport(event.getPlayer().getUniqueId());
    }

    private void markTeleport(java.util.UUID uuid) {
        PlayerData data = players.get(uuid);
        if (data != null) {
            data.markTeleport(System.currentTimeMillis());
        }
    }
}
