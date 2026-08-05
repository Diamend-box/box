package com.diamend.boxtutorial.guide;

import java.util.Locale;

/**
 * How a tutorial step notices it has been done.
 *
 * <p>The set is deliberately small. A tutorial is not an achievement plugin —
 * it needs to recognise the handful of things a new player does in their first
 * ten minutes, and anything beyond that is better left to a plugin whose job it
 * is. {@link #READ} exists because most of what a newcomer to boxpvp is missing
 * is knowledge, not a completed action: the step is done when they've read it.
 */
public enum StepTrigger {

    /** Completed by clicking the step in the menu. For explanation-only steps. */
    READ("Read it", false),

    /** Completed only by {@code /tutorial complete}, or by another plugin. */
    MANUAL("Granted by staff", false),

    /** The player runs a command, e.g. {@code sell} for {@code /sell all}. */
    RUN_COMMAND("Run a command", true),

    BREAK_BLOCK("Break blocks", true),
    PLACE_BLOCK("Place blocks", true),
    PICK_UP_ITEM("Pick up items", true),
    CRAFT_ITEM("Craft items", true),
    KILL_MOB("Kill mobs", true),
    KILL_PLAYER("Kill players", false),

    /** Buy something from the tutorial trader. Target is what you get. */
    BUY_ITEM("Buy from the trader", true),

    /** Simply be carrying it — the check for "you now have a pickaxe". */
    HAVE_ITEM("Carry an item", true),

    /** Entering a world by name, e.g. a {@code warzone} world. */
    ENTER_WORLD("Enter a world", true),

    /** Standing within {@code world;x;y;z;radius}. */
    REACH_LOCATION("Reach a place", true),

    /** Minutes spent online while the tutorial is running. */
    PLAYTIME_MINUTES("Play for a while", false);

    private final String display;
    private final boolean usesTarget;

    StepTrigger(String display, boolean usesTarget) {
        this.display = display;
        this.usesTarget = usesTarget;
    }

    public String display() {
        return display;
    }

    /** True when the step's {@code target:} means something for this trigger. */
    public boolean usesTarget() {
        return usesTarget;
    }

    /** True when nothing the player does in the world can complete this step. */
    public boolean isPassive() {
        return this == READ || this == MANUAL;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a config value, returning {@code fallback} for anything unknown. */
    public static StepTrigger fromName(String name, StepTrigger fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String cleaned = name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (StepTrigger trigger : values()) {
            if (trigger.name().equals(cleaned)) {
                return trigger;
            }
        }
        return fallback;
    }
}
