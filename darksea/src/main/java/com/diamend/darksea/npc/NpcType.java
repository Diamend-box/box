package com.diamend.darksea.npc;

import java.util.Locale;

/**
 * The five people who live on the home island. Every one of them is the same
 * frozen villager under the hood — what differs is the profession skin, the
 * board that opens on right-click, and which of the special actions (relic
 * waking, heart clues) that board carries.
 *
 * <p>Bukkit-free on purpose: the profession is held as its vanilla registry
 * name and resolved in {@link NpcService}, which keeps this enum and the shop
 * tables unit-testable without a server.
 */
public enum NpcType {

    /**
     * The honest one. Buys sea salvage at a fair-but-thin price and sells the
     * basics a captain forgot to pack. Stands near the middle of the outpost.
     */
    REFUGEE_TRADER("refugee_trader", "fisherman",
            "<aqua>Refugee Trader</aqua>", "<dark_aqua>Refugee Trader</dark_aqua>"),

    /**
     * Wakes dormant relics for their Chronon price — the only one who will —
     * and takes the relics you have no use for off your hands.
     */
    ARTIFICER("artificer", "toolsmith",
            "<color:#c9a227>Artificer</color>", "<color:#c9a227>Artificer</color>"),

    /**
     * Rotates stock every sea reset, charges what he likes, and is the only
     * source of Hullpiercer arrows. Pays better than the refugee for salvage,
     * which is the entire reason anyone puts up with him.
     */
    BLACK_MARKET("black_market", "cleric",
            "<dark_purple>Black Market</dark_purple>", "<dark_purple>Black Market</dark_purple>"),

    /**
     * The old boat expert, who seems to know rather more than a boat expert
     * should. Sells hulls, patches wrecks, and — for escalating money — tells
     * you what he has heard about the Heart.
     */
    BOAT_EXPERT("boat_expert", "fletcher",
            "<color:#7fb2c4>Old Boat Expert</color>", "<color:#7fb2c4>Old Boat Expert</color>"),

    /**
     * Tonics, salves and food that is actually food.
     */
    APOTHECARY("apothecary", "farmer",
            "<color:#4db6ac>Apothecary</color>", "<color:#4db6ac>Apothecary</color>");

    private final String id;
    private final String profession;
    private final String displayName;
    private final String menuTitle;

    NpcType(String id, String profession, String displayName, String menuTitle) {
        this.id = id;
        this.profession = profession;
        this.displayName = displayName;
        this.menuTitle = menuTitle;
    }

    public String id() {
        return id;
    }

    /** Vanilla villager profession registry name, e.g. {@code toolsmith}. */
    public String profession() {
        return profession;
    }

    /** MiniMessage name floating over the villager. */
    public String displayName() {
        return displayName;
    }

    /** MiniMessage title of the board this NPC opens. */
    public String menuTitle() {
        return menuTitle;
    }

    /** Whether this NPC's board carries the "wake the relic in your hand" button. */
    public boolean wakesRelics() {
        return this == ARTIFICER;
    }

    /** Whether this NPC sells rumours about the Undrowned Heart. */
    public boolean sellsHeartClues() {
        return this == BOAT_EXPERT;
    }

    /**
     * Whether this NPC's stock rotates with the sea. Only the black market
     * does; everyone else keeps a fixed shelf so players can plan a run.
     */
    public boolean rotatesStock() {
        return this == BLACK_MARKET;
    }

    /** Lookup by id (case-insensitive), or null. */
    public static NpcType byId(String id) {
        if (id == null) {
            return null;
        }
        String needle = id.toLowerCase(Locale.ROOT);
        for (NpcType type : values()) {
            if (type.id.equals(needle)) {
                return type;
            }
        }
        return null;
    }
}
