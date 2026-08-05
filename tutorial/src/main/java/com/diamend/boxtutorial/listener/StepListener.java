package com.diamend.boxtutorial.listener;

import com.diamend.boxtutorial.BoxTutorialPlugin;
import com.diamend.boxtutorial.guide.StepTrigger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Turns the handful of things a new player does into tutorial progress.
 *
 * <p>Every handler is a one-liner into {@link com.diamend.boxtutorial.guide.TutorialService#record},
 * which decides whether anybody is actually waiting on that event. Handlers run
 * at {@code MONITOR} and skip cancelled events: a block another plugin refused
 * to let them break is not a block they broke.
 */
public class StepListener implements Listener {

    private final BoxTutorialPlugin plugin;

    public StepListener(BoxTutorialPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.service().record(event.getPlayer(), StepTrigger.BREAK_BLOCK,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        plugin.service().record(event.getPlayer(), StepTrigger.PLACE_BLOCK,
                event.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        plugin.service().record(player, StepTrigger.PICK_UP_ITEM,
                stack.getType().name(), stack.getAmount());
    }

    /**
     * Crafting counts one result stack per craft. Shift-clicking a stack out of
     * the grid is several crafts in one event and is counted as one — a
     * tutorial step is "craft a pickaxe", not an inventory audit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        plugin.service().record(player, StepTrigger.CRAFT_ITEM,
                result.getType().name(), Math.max(1, result.getAmount()));
    }

    /**
     * Mob kills. {@link PlayerDeathEvent} is a subclass of this one, so player
     * deaths are stepped over here and handled below — otherwise a kill would
     * count as both a mob and a player.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent || event.getEntity() instanceof Player) {
            return;
        }
        plugin.service().record(event.getEntity().getKiller(), StepTrigger.KILL_MOB,
                event.getEntity().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.equals(event.getEntity())) {
            plugin.service().record(killer, StepTrigger.KILL_PLAYER,
                    event.getEntity().getName(), 1);
        }
    }

    /**
     * Commands are counted when they're typed, not when they succeed — the
     * plugin has no way to know whether {@code /sell} worked, and a newcomer who
     * typed the right thing has done the part the tutorial was teaching.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.isBlank()) {
            return;
        }
        plugin.service().record(event.getPlayer(), StepTrigger.RUN_COMMAND, message.trim(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.service().record(event.getPlayer(), StepTrigger.ENTER_WORLD,
                event.getPlayer().getWorld().getName(), 1);
        plugin.guide().refresh(event.getPlayer());
    }
}
