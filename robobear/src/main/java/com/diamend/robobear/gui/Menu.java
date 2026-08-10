package com.diamend.robobear.gui;

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

    /** Builds (if needed) and opens this menu for a player. */
    void open(Player player);

    /** Handles a click in this menu. Cancelled first unless {@link #cancelClick} says otherwise. */
    void handleClick(InventoryClickEvent event);

    /**
     * Whether this click should be cancelled — the default, since menus are
     * read-only. The reward editor overrides it so items can actually be put in.
     *
     * @param event the click, or null when asking about a drag in general
     */
    default boolean cancelClick(InventoryClickEvent event) {
        return true;
    }

    /** Called when this menu's inventory closes. */
    default void onClose() {
    }

    @Override
    Inventory getInventory();
}
