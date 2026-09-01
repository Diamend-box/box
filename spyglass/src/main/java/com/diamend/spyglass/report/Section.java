package com.diamend.spyglass.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The parts of a player you can ask for: {@code /spy Notch inventory}.
 *
 * <p>{@link #offline} records whether the section can be answered for someone
 * who isn't logged in — most can, because the answer is in their save file, but
 * a live scoreboard or an effective permission list only exists while they are
 * connected.
 */
public enum Section {

    OVERVIEW("overview", "who they are, how they are, where they are", true, "o", "summary", "info"),
    IDENTITY("identity", "name, UUID, op, ban and whitelist state, first seen", true, "id", "who"),
    CONNECTION("connection", "address, ping, client brand, session times", true, "conn", "net"),
    VITALS("vitals", "health, hunger, air, fire, XP, game mode, flags", true, "health", "state"),
    POSITION("position", "world, coordinates, facing, biome, respawn point", true, "pos", "loc", "location"),
    INVENTORY("inventory", "all 41 slots, with what is in them", true, "inv", "items"),
    ENDERCHEST("enderchest", "the ender chest, all 27 slots", true, "ec", "ender"),
    ARMOR("armor", "what they are wearing and holding", true, "gear", "equipment"),
    EFFECTS("effects", "active potion effects", true, "potions", "fx"),
    ATTRIBUTES("attributes", "attribute values and their modifiers", true, "attrs"),
    STATS("stats", "every statistic they have a number for", true, "statistics"),
    ADVANCEMENTS("advancements", "advancement progress, done and part-done", true, "adv", "achievements"),
    PERMISSIONS("permissions", "effective permission nodes", false, "perms", "perm"),
    SCOREBOARD("scoreboard", "team, objectives and scores", false, "score", "sb"),
    DATA("data", "persistent data container, scoreboard tags", true, "pdc", "tags"),
    RECIPES("recipes", "recipes they have unlocked", true, "recipe", "book"),
    ITEM("item", "everything about one slot: /spy <player> item <slot>", true, "slot"),
    NBT("nbt", "the raw save tree: /spy <player> nbt [path]", true, "raw", "dat"),
    ALL("all", "every section at once — best paired with /spy dump", true, "everything");

    private final String id;
    private final String summary;
    private final boolean offline;
    private final List<String> aliases;

    Section(String id, String summary, boolean offline, String... aliases) {
        this.id = id;
        this.summary = summary;
        this.offline = offline;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    public String summary() {
        return summary;
    }

    /** True when this section can be answered from a save file alone. */
    public boolean offline() {
        return offline;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** The section that name refers to, by id or alias, or null. */
    public static Section byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Section section : values()) {
            if (section.id.equals(wanted) || section.aliases.contains(wanted)) {
                return section;
            }
        }
        return null;
    }

    /** Every name that resolves to a section, for tab completion. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Section section : values()) {
            out.add(section.id);
        }
        return out;
    }

    /** The sections {@code /spy <player> all} walks, in the order it walks them. */
    public static List<Section> everything() {
        return List.of(IDENTITY, CONNECTION, VITALS, POSITION, ARMOR, INVENTORY, ENDERCHEST,
                EFFECTS, ATTRIBUTES, DATA, SCOREBOARD, RECIPES, ADVANCEMENTS, STATS, PERMISSIONS);
    }
}
