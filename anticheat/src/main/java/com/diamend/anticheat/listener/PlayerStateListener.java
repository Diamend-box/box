package com.diamend.anticheat.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

import com.diamend.anticheat.alert.AlertManager;
import com.diamend.anticheat.player.PlayerData;
import com.diamend.anticheat.player.PlayerDataManager;
import com.diamend.anticheat.protect.EntityCullingTask;

/**
 * Maintains {@link PlayerData} lifecycle and records the events that grant a
 * grace window to the movement checks: joins, teleports, respawns and
 * server-granted velocity (knockback / explosions). During those windows the
 * {@link com.diamend.anticheat.exempt.ExemptionManager} and the velocity grace
 * skip movement/combat checks so the position discontinuity they cause never
 * reads as cheating.
 *
 * <p>These few Bukkit events are lifecycle bookkeeping only — never the hot path
 * of a check, which lives entirely on the packet pipeline as the design requires.
 */
public final class PlayerStateListener implements Listener {

    private final PlayerDataManager players;
    private final AlertManager alerts;
    private final EntityCullingTask culling;

    public PlayerStateListener(PlayerDataManager players, AlertManager alerts,
                               EntityCullingTask culling) {
        this.players = players;
        this.alerts = alerts;
        this.culling = culling;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        players.create(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        players.remove(uuid);
        alerts.clearVerbose(uuid);
        if (culling != null) {
            culling.forget(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        markTeleport(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        markTeleport(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = players.get(event.getPlayer().getUniqueId());
        if (data != null) {
            data.movement().markVelocity(System.currentTimeMillis());
        }
    }

    private void markTeleport(java.util.UUID uuid) {
        PlayerData data = players.get(uuid);
        if (data != null) {
            data.markTeleport(System.currentTimeMillis());
            data.movement().markVelocity(System.currentTimeMillis());
        }
    }
}
