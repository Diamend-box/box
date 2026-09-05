package com.diamend.darksea.relic;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The holder behind an open relic editor. Like the loot editor's, it records
 * what each slot showed at draw time rather than re-deriving it on click.
 *
 * <p>What it deliberately does not hold is the relic itself — only its id. A
 * board can sit open across a save that replaces the relic instance, and an
 * editor holding the old object would write the change it was showing back
 * over the change somebody else just made. Re-reading the registry on every
 * draw and every click costs a map lookup and removes the whole question.
 */
public final class RelicEditor implements InventoryHolder {

    /** Which board is open. */
    public enum Page { LIST, EDIT }

    /** A control on an editor board. */
    public enum Button {
        NEW, HELP, BACK,
        MATERIAL, NAME, LORE, BOOST, EFFECT, TIER, COST, BOOST_LINE,
        GIVE, DELETE
    }

    private final Page page;
    private final String relicId;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Map<Integer, String> relics = new HashMap<>();
    private Inventory inventory;

    RelicEditor(Page page, String relicId) {
        this.page = page;
        this.relicId = relicId;
    }

    Page page() {
        return page;
    }

    /** Which relic an EDIT board is editing. Null on the list. */
    String relicId() {
        return relicId;
    }

    void putButton(int slot, Button button) {
        buttons.put(slot, button);
    }

    void putRelic(int slot, String id) {
        relics.put(slot, id);
    }

    Button buttonAt(int slot) {
        return buttons.get(slot);
    }

    String relicAt(int slot) {
        return relics.get(slot);
    }

    void clear() {
        buttons.clear();
        relics.clear();
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
