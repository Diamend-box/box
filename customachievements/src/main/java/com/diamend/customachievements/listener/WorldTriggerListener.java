package com.diamend.customachievements.listener;

import com.diamend.customachievements.achievement.AchievementService;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Feeds movement and world-change events into REACH_LOCATION and
 * REACH_DIMENSION achievements. Move events are filtered down to full-block
 * changes so the location check stays cheap, and teleports/world changes/joins
 * are handled explicitly since they don't fire {@link PlayerMoveEvent}.
 */
public class WorldTriggerListener implements Listener {

    private final AchievementService service;

    public WorldTriggerListener(AchievementService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // Head rotation / sub-block movement only.
        }
        service.handleLocation(event.getPlayer(), to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.handleLocation(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        service.handleDimension(event.getPlayer(), event.getPlayer().getWorld());
        service.handleLocation(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.handleDimension(event.getPlayer(), event.getPlayer().getWorld());
        service.handleLocation(event.getPlayer(), event.getPlayer().getLocation());
    }
}
