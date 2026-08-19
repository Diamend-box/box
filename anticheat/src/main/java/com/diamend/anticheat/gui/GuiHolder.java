package com.diamend.anticheat.gui;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as one of the anticheat's GUIs so the click listener can
 * recognise (and fully control) it, and carries the little bit of context a
 * screen needs — which screen it is, and which player it's about.
 */
public final class GuiHolder implements InventoryHolder {

    /** Which anticheat screen this inventory represents. */
    public enum Screen {
        MAIN, PLAYERS, PLAYER_DETAIL
    }

    private final Screen screen;
    private final UUID target;
    private Inventory inventory;

    public GuiHolder(Screen screen, UUID target) {
        this.screen = screen;
        this.target = target;
    }

    public Screen screen() {
        return screen;
    }

    /** The player this screen is about (PLAYER_DETAIL only), else {@code null}. */
    public UUID target() {
        return target;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
