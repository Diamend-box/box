package com.diamend.customachievements.achievement;

/**
 * The kinds of events that can complete a custom achievement.
 *
 * <p>Each trigger either fires once ({@code MANUAL}, {@code REACH_LOCATION})
 * or accumulates progress up to the achievement's required amount. Some
 * triggers match against a "target" (a {@link org.bukkit.Material},
 * {@link org.bukkit.entity.EntityType}, world, location or MythicMobs mob
 * name), while others simply count occurrences.
 */
public enum TriggerType {

    /** Only granted through the command or another plugin's API. */
    MANUAL("Manual / Command", false, false),

    /** Break blocks of the target material. */
    BLOCK_BREAK("Break Blocks", true, true),

    /** Place blocks of the target material. */
    BLOCK_PLACE("Place Blocks", true, true),

    /** Kill entities of the target type. */
    ENTITY_KILL("Kill Entities", true, true),

    /** Kill MythicMobs mobs with the target internal name (requires MythicMobs). */
    MYTHIC_MOB_KILL("Kill Mythic Mobs", true, true),

    /** Craft items of the target material. */
    ITEM_CRAFT("Craft Items", true, true),

    /** Eat or drink items of the target material. */
    ITEM_CONSUME("Consume Items", true, true),

    /** Reel in anything while fishing. */
    FISH_CAUGHT("Catch Fish", false, true),

    /** Die as a player. */
    PLAYER_DEATH("Player Deaths", false, true),

    /** Accumulate minutes of playtime while online. */
    PLAYTIME_MINUTES("Playtime (minutes)", false, true),

    /** Enter a radius around a fixed point (target: world;x;y;z;radius). */
    REACH_LOCATION("Reach a Location", true, false),

    /** Enter a world/dimension, including custom ones (target: name, key or environment). */
    REACH_DIMENSION("Reach a Dimension", true, true);

    private final String display;
    private final boolean usesTarget;
    private final boolean usesAmount;

    TriggerType(String display, boolean usesTarget, boolean usesAmount) {
        this.display = display;
        this.usesTarget = usesTarget;
        this.usesAmount = usesAmount;
    }

    /** Human readable label shown in the editor GUI. */
    public String display() {
        return display;
    }

    /** Whether this trigger matches a target key. */
    public boolean usesTarget() {
        return usesTarget;
    }

    /** Whether a required amount is meaningful for this trigger. */
    public boolean isProgress() {
        return usesAmount;
    }

    public TriggerType next() {
        TriggerType[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public TriggerType prev() {
        TriggerType[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static TriggerType fromString(String raw) {
        if (raw == null) {
            return MANUAL;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MANUAL;
        }
    }
}
