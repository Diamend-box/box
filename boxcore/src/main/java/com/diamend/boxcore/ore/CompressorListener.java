package com.diamend.boxcore.ore;

import com.diamend.boxcore.BoxCorePlugin;
import com.diamend.boxcore.gui.CompactorMenu;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Event surface for the personal compactor.
 *
 * <p>The two hot paths — breaking blocks and picking items up — do no more than
 * add the player to a set. All the actual inventory work happens on the
 * module's throttled sweep, because regenerating cubes make
 * {@code BlockBreakEvent} fire far too often to scan an inventory inside it.
 *
 * <p>The rest of this class exists to stop a compactor being lost. It is the
 * only thing standing between a player and their compaction setup, so every
 * ordinary way an item leaves an inventory by accident — Q, dragging it out of
 * the window, dying, placing it as a block — is closed off. Putting it in a
 * chest still works: that is a decision, not an accident.
 */
public class CompressorListener implements Listener {

    private final BoxCorePlugin plugin;
    private final CompressorModule module;

    public CompressorListener(BoxCorePlugin plugin, CompressorModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ------------------------------------------------------------------
    // Noticing that a player gained items
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        module.markPending(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            module.markPending(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        module.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Closing a container is a cheap moment to re-check: a player who has just
     * pulled items out of a chest gained them without breaking a block.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
                && event.getInventory().getType() != InventoryType.CRAFTING) {
            module.markPending(player);
        }
    }

    // ------------------------------------------------------------------
    // Right-click: open a compactor, or expand a compacted stack
    // ------------------------------------------------------------------

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
        boolean compactor = module.compactors().isCompactor(held);
        if (!compactor && !plugin.ores().compressed().isCompressed(held)) {
            return;
        }
        // Let doors, chests and buttons win — neither opening a compactor nor
        // expanding a stack is worth making an item unable to open things.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
                return;
            }
        }
        event.setCancelled(true);

        if (compactor) {
            new CompactorMenu(plugin, module, player.getInventory().getHeldItemSlot()).open(player);
            return;
        }

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

    // ------------------------------------------------------------------
    // Keeping the compactor
    // ------------------------------------------------------------------

    /** Q, or any other path that throws the held item on the floor. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (module.compactors().isCompactor(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "compactor-kept");
        }
    }

    /**
     * Dropping from inside an inventory screen: Q over a slot, or dragging the
     * item out of the window and releasing.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        boolean dropKey = click == ClickType.DROP || click == ClickType.CONTROL_DROP;
        boolean outside = event.getSlotType() == InventoryType.SlotType.OUTSIDE;
        if (!dropKey && !outside) {
            return;
        }
        ItemStack leaving = dropKey ? event.getCurrentItem() : event.getCursor();
        if (module.compactors().isCompactor(leaving)) {
            event.setCancelled(true);
            plugin.messages().send(player, "compactor-kept");
        }
    }

    /**
     * A compactor skinned as a hopper is still a hopper to the place routine,
     * and placing it would consume it. The interact handler above already
     * cancels the click, but this closes the path outright rather than relying
     * on event ordering to protect someone's compactor.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (module.compactors().isCompactor(event.getItemInHand())) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "compactor-kept");
        }
    }

    /** Pull compactors out of the death drop and hold them for the respawn. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> kept = new ArrayList<>();
        event.getDrops().removeIf(stack -> {
            if (module.compactors().isCompactor(stack)) {
                kept.add(stack.clone());
                return true;
            }
            return false;
        });
        module.keepThroughDeath(event.getEntity().getUniqueId(), kept);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> kept = module.reclaimAfterDeath(player.getUniqueId());
        if (kept.isEmpty()) {
            return;
        }
        // The inventory is not settled until after the respawn resolves, so
        // hand them back on the next tick rather than into a state still being
        // rebuilt underneath us.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (ItemStack stack : kept) {
                module.give(player, stack);
            }
        });
    }
}
