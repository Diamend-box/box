package com.diamend.boxcore.ore;

import com.diamend.boxcore.BoxCorePlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Event surface for the auto-compressor.
 *
 * <p>The two hot paths — breaking blocks and picking items up — do no more than
 * add the player to a set. All the actual inventory work happens on the
 * module's throttled sweep, because regenerating cubes make
 * {@code BlockBreakEvent} fire far too often to scan an inventory inside it.
 */
public class CompressorListener implements Listener {

    private final BoxCorePlugin plugin;
    private final CompressorModule module;

    public CompressorListener(BoxCorePlugin plugin, CompressorModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        module.markPending(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && plugin.ores().isOre(event.getItem().getItemStack().getType())) {
            module.markPending(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Right-click a compressed stack to expand one unit; sneak to expand the
     * whole stack. Expanding exists because a few vanilla systems still take
     * raw ore — lapis in an enchanting table, diamonds in an anvil — and a
     * player whose ore is all compressed would otherwise be locked out of them.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!plugin.ores().compressed().isCompressed(held)) {
            return;
        }
        // Let doors, chests and buttons win — expanding is not worth making a
        // stack of ore unable to open things.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
                return;
            }
        }
        event.setCancelled(true);

        int wanted = player.isSneaking() ? held.getAmount() : 1;
        int expanded = module.expand(player, wanted);
        if (expanded <= 0) {
            plugin.messages().send(player, "compressor-no-room");
            return;
        }
        plugin.messages().send(player, "compressor-expanded",
                "amount", expanded,
                "plural", expanded == 1 ? "" : "s",
                "ore", CompressedOre.displayName(held.getType()),
                "seconds", module.graceSeconds());
    }

    /**
     * Closing a container is a cheap moment to re-check: a player who has just
     * pulled ore out of a chest gained items without breaking a block.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            module.markPending(player);
        }
    }
}
