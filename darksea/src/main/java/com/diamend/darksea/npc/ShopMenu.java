package com.diamend.darksea.npc;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * The holder behind an open shop board. It carries the slot map built when the
 * board was drawn, so a click resolves to exactly the offer or action that was
 * rendered — no slot arithmetic at click time, and a rotated black-market
 * shelf can never sell the wrong line.
 */
public final class ShopMenu implements InventoryHolder {

    /** Board buttons that aren't item trades. */
    public enum Action { WAKE_RELIC, BUY_CLUE }

    private final String npcId;
    private final NpcType type;
    private final Map<Integer, ShopOffer> offers = new HashMap<>();
    private final Map<Integer, Action> actions = new HashMap<>();
    private Inventory inventory;

    ShopMenu(String npcId, NpcType type) {
        this.npcId = npcId;
        this.type = type;
    }

    String npcId() {
        return npcId;
    }

    NpcType type() {
        return type;
    }

    void put(int slot, ShopOffer offer) {
        offers.put(slot, offer);
    }

    void put(int slot, Action action) {
        actions.put(slot, action);
    }

    ShopOffer offerAt(int slot) {
        return offers.get(slot);
    }

    Action actionAt(int slot) {
        return actions.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
