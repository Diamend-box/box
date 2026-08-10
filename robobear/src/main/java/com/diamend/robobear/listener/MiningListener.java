package com.diamend.robobear.listener;

import com.diamend.robobear.RoboBearPlugin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Feeds broken blocks and dead mobs into the run counter.
 *
 * <p>Written for volume. A server with regenerating cubes fires
 * {@code BlockBreakEvent} constantly, so this handler does the cheapest possible
 * thing first — ask whether this player has a run at all — and returns. There is
 * no disk access, no reflection and no allocation on the path.
 *
 * <p>{@code MONITOR} with {@code ignoreCancelled}: the count should reflect what
 * actually happened, after protection plugins and the anticheat have had a say.
 */
public class MiningListener implements Listener {

    private final RoboBearPlugin plugin;

    public MiningListener(RoboBearPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.service().onBlockBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.service().onBlockPlace(event.getPlayer(), event.getBlockPlaced());
    }

    /**
     * Credits a hostile-mob kill.
     *
     * <p>{@link Monster} rather than a configurable entity list: the objective
     * reads "kill hostile mobs", and Bukkit's own idea of what is hostile is the
     * one that will stay right as mobs are added to the game.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            plugin.service().onMobKill(killer);
        }
    }
}
