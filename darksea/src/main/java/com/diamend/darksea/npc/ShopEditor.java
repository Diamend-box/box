package com.diamend.darksea.npc;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The holder behind an open shop editor. It remembers which list is being
 * edited and which slot showed which line at draw time, so a click resolves to
 * exactly the line that was rendered — the lists shift under deletions, and
 * re-deriving an index at click time is how an editor deletes the wrong row.
 */
public final class ShopEditor implements InventoryHolder {

    /** Which board is open. */
    public enum Page { PICKER, LIST, SETTINGS }

    /** Which list a {@link Page#LIST} board is editing. */
    public enum ListKind {
        /** What an NPC sells to players. */
        NPC_BUY,
        /** What an NPC buys from them. */
        NPC_SELL,
        /** The black market's rotating shelf. */
        POOL,
        /** The shared salvage prices. */
        SALVAGE;

        public ShopOffer.Kind offerKind() {
            return this == NPC_SELL || this == SALVAGE
                    ? ShopOffer.Kind.SELL : ShopOffer.Kind.BUY;
        }

        public boolean isNpcList() {
            return this == NPC_BUY || this == NPC_SELL;
        }
    }

    /** A control button on an editor board. */
    public enum Button {
        BACK, TOGGLE_SALVAGE, OPEN_SETTINGS,
        MARKUP_UP, MARKUP_DOWN,
        SALVAGE_RATE_UP, SALVAGE_RATE_DOWN,
        SLOTS_UP, SLOTS_DOWN,
        ADD_CLUE_RUNG, DROP_CLUE_RUNG
    }

    private final Page page;
    private final ListKind listKind;
    private final NpcType npc;
    private final Map<Integer, Integer> lineIndexes = new HashMap<>();
    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Map<Integer, ListKind> lists = new HashMap<>();
    private final Map<Integer, NpcType> npcs = new HashMap<>();
    private final Map<Integer, Integer> clueRungs = new HashMap<>();
    private Inventory inventory;

    ShopEditor(Page page, ListKind listKind, NpcType npc) {
        this.page = page;
        this.listKind = listKind;
        this.npc = npc;
    }

    Page page() {
        return page;
    }

    ListKind listKind() {
        return listKind;
    }

    NpcType npc() {
        return npc;
    }

    /** The id of the list being edited, for the salvage-taker toggle. */
    String npcId() {
        return npc == null ? null : npc.id();
    }

    void putLine(int slot, int index) {
        lineIndexes.put(slot, index);
    }

    void putButton(int slot, Button button) {
        buttons.put(slot, button);
    }

    /** Picker entries carry both which NPC and which of their two lists. */
    void putListEntry(int slot, NpcType type, ListKind kind) {
        if (type != null) {
            npcs.put(slot, type);
        }
        lists.put(slot, kind);
    }

    void putClueRung(int slot, int index) {
        clueRungs.put(slot, index);
    }

    /** Index into the edited list, or null if this slot isn't a line. */
    Integer lineAt(int slot) {
        return lineIndexes.get(slot);
    }

    Button buttonAt(int slot) {
        return buttons.get(slot);
    }

    ListKind listAt(int slot) {
        return lists.get(slot);
    }

    NpcType npcAt(int slot) {
        return npcs.get(slot);
    }

    Integer clueRungAt(int slot) {
        return clueRungs.get(slot);
    }

    void clear() {
        lineIndexes.clear();
        buttons.clear();
        lists.clear();
        npcs.clear();
        clueRungs.clear();
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
