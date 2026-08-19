package com.diamend.anticheat.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Turns clicks in an anticheat GUI into actions and, crucially, keeps players
 * from pulling the control-panel items out into their own inventory: every click
 * and drag inside one of our screens is cancelled before anything else runs.
 */
public final class GuiListener implements Listener {

    private final AntiCheatGui gui;

    public GuiListener(AntiCheatGui gui) {
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder holder)) {
            return;
        }
        // Our GUI is read-only: cancel every click regardless of where it lands.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            // Clicked in their own inventory; already cancelled, nothing to do.
            return;
        }
        gui.handleClick(player, holder, raw);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }
}
