package com.diamend.customachievements.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A custom GUI. Implementations act as their own {@link InventoryHolder} so the
 * single {@link GuiListener} can identify and route clicks by holder type
 * rather than by fragile title-string comparisons.
 */
public interface Menu extends InventoryHolder {

    /** Builds (if needed) and opens this menu for the given player. */
    void open(Player player);

    /** Handles a click that occurred in this menu. The event is already cancelled. */
    void handleClick(InventoryClickEvent event);

    @Override
    Inventory getInventory();
}
