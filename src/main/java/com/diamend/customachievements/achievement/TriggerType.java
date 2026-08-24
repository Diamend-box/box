package com.diamend.customachievements.achievement;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The kinds of events that can complete an achievement objective.
 */
public enum TriggerType {

    MANUAL("Manual / Command", false, false, Material.COMMAND_BLOCK),
    BLOCK_BREAK("Break Blocks", true, true, Material.DIAMOND_PICKAXE),
    BLOCK_PLACE("Place Blocks", true, true, Material.BRICKS),
    ENTITY_KILL("Kill Entities", true, true, Material.DIAMOND_SWORD),
    MYTHIC_MOB_KILL("Kill Mythic Mobs", true, true, Material.WITHER_SKELETON_SKULL),
    AURASKILLS_LEVEL("AuraSkills Level", true, true, Material.EXPERIENCE_BOTTLE),
    ITEM_CRAFT("Craft Items", true, true, Material.CRAFTING_TABLE),
    ITEM_CONSUME("Consume Items", true, true, Material.COOKED_BEEF),
    ITEM_OBTAIN("Obtain Items", true, true, Material.HOPPER),
    ITEM_HAVE("Have Items", true, true, Material.CHEST),
    FISH_CAUGHT("Catch Fish", false, true, Material.FISHING_ROD),
    PLAYER_DEATH("Player Deaths", true, true, Material.SKELETON_SKULL),
    PLAYTIME_HOURS("Playtime (hours)", false, true, Material.CLOCK),
    REACH_LOCATION("Reach a Location", true, false, Material.COMPASS),
    REACH_DIMENSION("Reach a Dimension", true, true, Material.END_PORTAL_FRAME);

    private final String display;
    private final boolean usesTarget;
    private final boolean usesAmount;
    private final Material icon;

    TriggerType(String display, boolean usesTarget, boolean usesAmount, Material icon) {
        this.display = display;
        this.usesTarget = usesTarget;
        this.usesAmount = usesAmount;
        this.icon = icon;
    }

    public String display() {
        return display;
    }

    public boolean usesTarget() {
        return usesTarget;
    }

    /** Whether a required amount is meaningful for this trigger. */
    public boolean isProgress() {
        return usesAmount;
    }

    /** Icon used to represent this trigger in the picker GUI. */
    public Material icon() {
        return icon;
    }

    /** Triggers whose progress is a live gauge (set to a value) rather than a running count. */
    public boolean isGauge() {
        return this == PLAYTIME_HOURS || this == AURASKILLS_LEVEL || this == ITEM_HAVE;
    }

    /** Item-based triggers that can optionally match by custom item name. */
    public boolean isItemTrigger() {
        return this == ITEM_CRAFT || this == ITEM_CONSUME || this == ITEM_OBTAIN
                || this == ITEM_HAVE;
    }

    public static TriggerType fromString(String raw) {
        if (raw == null) {
            return MANUAL;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.equals("PLAYTIME_MINUTES")) {
            return PLAYTIME_HOURS; // legacy alias
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return MANUAL;
        }
    }
}
