package com.diamend.boxcore.travel;

import com.diamend.boxcore.BoxCorePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Event surface for fast travel: what tags a fight, what interrupts a trip, and
 * what counts as finding somewhere.
 *
 * <p>The move handler is the one hot path here. It returns immediately unless
 * the player actually changed block, because looking around produces a stream
 * of move events and neither the discovery scan nor the trip check is worth
 * doing several times a tick per player.
 */
public class TravelListener implements Listener {

    private final BoxCorePlugin plugin;
    private final TravelModule module;

    public TravelListener(BoxCorePlugin plugin, TravelModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    /** Tags both sides of a player-versus-player hit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        module.combat().tag(victim);
        module.combat().tag(attacker);
    }

    /** The player behind a hit, following an arrow back to whoever fired it. */
    private Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * Any damage stops a trip, not just PvP. A warmup that survived falling in
     * lava would be a strange thing to explain.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            module.travel().onHurt(player);
        }
    }

    // ------------------------------------------------------------------
    // Moving
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || !changedBlock(from, to)) {
            return;
        }
        Player player = event.getPlayer();
        module.travel().onMove(player, to);
        module.checkDiscovery(player, to);
    }

    private boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || (from.getWorld() != null && !from.getWorld().equals(to.getWorld()));
    }

    // ------------------------------------------------------------------
    // Leaving
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.travel().forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // The damage handler has already cancelled the trip; this clears the
        // combat tag so a respawn isn't stuck waiting it out at the graveyard.
        module.travel().forget(event.getEntity().getUniqueId());
    }
}
