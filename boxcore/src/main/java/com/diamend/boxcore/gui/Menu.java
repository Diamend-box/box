package com.diamend.boxcore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * A custom GUI. Implementations are their own {@link InventoryHolder} so the
 * single {@link GuiListener} can route clicks by holder identity rather than by
 * fragile title-string comparison.
 */
public interface Menu extends InventoryHolder {

    /** Builds and opens this menu for a player. */
    void open(Player player);

    /** Handles a click inside this menu. Clicks are always cancelled first. */
    void handleClick(InventoryClickEvent event);

    @Override
    Inventory getInventory();
}
