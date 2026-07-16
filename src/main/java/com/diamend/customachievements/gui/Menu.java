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

    /** Handles a click that occurred in this menu. Cancelled unless {@link #cancelClick} says otherwise. */
    void handleClick(InventoryClickEvent event);

    /**
     * Whether this click should be cancelled (the default — GUIs are read-only).
     * Editable menus (e.g. the reward-items picker) override this to allow item
     * placement in some slots.
     */
    default boolean cancelClick(InventoryClickEvent event) {
        return true;
    }

    /** Called when the menu's inventory is closed. */
    default void onClose() {
    }

    @Override
    Inventory getInventory();
}
