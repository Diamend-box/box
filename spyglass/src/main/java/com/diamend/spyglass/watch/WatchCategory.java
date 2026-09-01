package com.diamend.spyglass.watch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The kinds of thing a live watch can report.
 *
 * <p>Split so a console can follow what someone says and builds without also
 * getting a line for every step they take — {@link #MOVEMENT} is off by default
 * for exactly that reason.
 */
public enum WatchCategory {

    CHAT("chat", "what they say"),
    COMMAND("command", "commands they run"),
    CONNECTION("connection", "joining, leaving, teleports, world changes"),
    MOVEMENT("movement", "where they are, sampled; sneaking and sprinting"),
    INVENTORY("inventory", "clicks, drops, pickups, crafting, eating"),
    BLOCKS("blocks", "breaking, placing, signs, buckets"),
    COMBAT("combat", "damage dealt and taken, deaths"),
    STATE("state", "game mode, level, effects, advancements");

    private final String id;
    private final String summary;

    WatchCategory(String id, String summary) {
        this.id = id;
        this.summary = summary;
    }

    public String id() {
        return id;
    }

    public String summary() {
        return summary;
    }

    public static WatchCategory byName(String name) {
        if (name == null) {
            return null;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (WatchCategory category : values()) {
            if (category.id.equals(wanted)) {
                return category;
            }
        }
        return null;
    }

    /**
     * Reads a list of category names. {@code all} means everything; unknown
     * names are ignored, so a typo costs you one category rather than the watch.
     */
    public static Set<WatchCategory> parse(Collection<String> names) {
        Set<WatchCategory> out = EnumSet.noneOf(WatchCategory.class);
        if (names == null) {
            return out;
        }
        for (String name : names) {
            if (name == null) {
                continue;
            }
            if (name.trim().equalsIgnoreCase("all")) {
                return EnumSet.allOf(WatchCategory.class);
            }
            WatchCategory category = byName(name);
            if (category != null) {
                out.add(category);
            }
        }
        return out;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (WatchCategory category : values()) {
            out.add(category.id);
        }
        out.add("all");
        return out;
    }

    /** Renders a set the way the config and the command both accept it back. */
    public static String describe(Set<WatchCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return "none";
        }
        if (categories.size() == values().length) {
            return "all";
        }
        List<String> out = new ArrayList<>();
        for (WatchCategory category : values()) {
            if (categories.contains(category)) {
                out.add(category.id);
            }
        }
        return String.join(", ", out);
    }
}
