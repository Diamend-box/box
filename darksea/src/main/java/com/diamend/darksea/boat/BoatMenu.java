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

    /** Which board is open: the main wheel or the stat-point Outfit page. */
    enum Page { MAIN, OUTFIT }

    private final UUID boatId;
    private final Page page;
    private Inventory inventory;

    BoatMenu(UUID boatId, Page page) {
        this.boatId = boatId;
        this.page = page;
    }

    UUID boatId() {
        return boatId;
    }

    Page page() {
        return page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
