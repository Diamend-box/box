package com.diamend.darksea.relic;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The holder behind an open reliquary. It carries no state beyond the
 * inventory itself — the bag's contents live in player data, and the board is
 * rebuilt from that on every click, so there is nothing here to go stale.
 */
public final class ReliquaryMenu implements InventoryHolder {

    private Inventory inventory;

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
