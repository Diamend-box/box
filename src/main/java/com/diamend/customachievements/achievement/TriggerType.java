package com.diamend.customachievements.achievement;

/**
 * The kinds of events that can complete a custom achievement.
 *
 * <p>Each trigger either fires once ({@code MANUAL}) or accumulates progress
 * up to the achievement's required amount. Some triggers match against a
 * "target" (a {@link org.bukkit.Material} or {@link org.bukkit.entity.EntityType}
 * name), while others simply count occurrences.
 */
public enum TriggerType {

    /** Only granted through the command or another plugin's API. */
    MANUAL("Manual / Command", false),

    /** Break blocks of the target material. */
    BLOCK_BREAK("Break Blocks", true),

    /** Place blocks of the target material. */
    BLOCK_PLACE("Place Blocks", true),

    /** Kill entities of the target type. */
    ENTITY_KILL("Kill Entities", true),

    /** Craft items of the target material. */
    ITEM_CRAFT("Craft Items", true),

    /** Eat or drink items of the target material. */
    ITEM_CONSUME("Consume Items", true),

    /** Reel in anything while fishing. */
    FISH_CAUGHT("Catch Fish", false),

    /** Die as a player. */
    PLAYER_DEATH("Player Deaths", false),

    /** Accumulate minutes of playtime while online. */
    PLAYTIME_MINUTES("Playtime (minutes)", false);

    private final String display;
    private final boolean usesTarget;

    TriggerType(String display, boolean usesTarget) {
        this.display = display;
        this.usesTarget = usesTarget;
    }

    /** Human readable label shown in the editor GUI. */
    public String display() {
        return display;
    }

    /** Whether this trigger matches a Material/EntityType target. */
    public boolean usesTarget() {
        return usesTarget;
    }

    /** Whether a required amount is meaningful (everything except MANUAL). */
    public boolean isProgress() {
        return this != MANUAL;
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
