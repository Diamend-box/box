package com.diamend.darksea.boat;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * The holder behind an open boat wheel: it just remembers which boat entity the
 * menu is bound to, so a click acts on the right hull even if the player has
 * several boats. {@link BoatMenuService} fills and refreshes the inventory.
 */
public final class BoatMenu implements InventoryHolder {

    private final UUID boatId;
    private Inventory inventory;

    BoatMenu(UUID boatId) {
        this.boatId = boatId;
    }

    UUID boatId() {
        return boatId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
