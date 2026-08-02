package com.diamend.darksea.loot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The holder behind an open loot editor. Like the shop editor's, it records
 * what each slot showed at draw time rather than re-deriving it on click — the
 * added list shifts under a deletion, and an editor that recomputes an index
 * at click time is an editor that eventually deletes the wrong row.
 */
public final class LootEditor implements InventoryHolder {

    /** Which board is open. */
    public enum Page { PICKER, LIST }

    /** A control button on an editor board. */
    public enum Button { BACK }

    private final Page page;
    private final CustomLoot.Key key;
    private final Map<Integer, Integer> addedIndexes = new HashMap<>();
    private final Map<Integer, CustomLoot.Key> tables = new HashMap<>();
    private final Map<Integer, Button> buttons = new HashMap<>();
    private Inventory inventory;

    LootEditor(Page page, CustomLoot.Key key) {
        this.page = page;
        this.key = key;
    }

    Page page() {
        return page;
    }

    /** Which table a LIST board is editing. */
    CustomLoot.Key key() {
        return key;
    }

    void putAdded(int slot, int index) {
        addedIndexes.put(slot, index);
    }

    void putTable(int slot, CustomLoot.Key table) {
        tables.put(slot, table);
    }

    void putButton(int slot, Button button) {
        buttons.put(slot, button);
    }

    /** Index into the overlay's list, or null if this slot is a shipped line or a control. */
    Integer addedAt(int slot) {
        return addedIndexes.get(slot);
    }

    CustomLoot.Key tableAt(int slot) {
        return tables.get(slot);
    }

    Button buttonAt(int slot) {
        return buttons.get(slot);
    }

    void clear() {
        addedIndexes.clear();
        tables.clear();
        buttons.clear();
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
