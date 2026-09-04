package com.diamend.customachievements.achievement;

import org.bukkit.Material;

/**
 * A named family an objective can target instead of one specific value — every
 * kind of log ({@link MaterialGroup#LOGS}), every hostile mob
 * ({@link EntityGroup#HOSTILE}), and so on.
 *
 * <p>Groups are stored in an objective's target field as {@code #ID} (e.g.
 * {@code #LOGS}, {@code #HOSTILE}), mirroring Minecraft's own {@code #minecraft:logs}
 * tag syntax. The leading {@code #} can't collide with a material or entity name,
 * so objectives that name a single value are unaffected.
 */
public interface TargetGroup {

    /** Marks a target string as a group rather than a single value. */
    String PREFIX = "#";

    /** The group's stable id, e.g. {@code LOGS} — what goes after the {@code #}. */
    String id();

    /** Human-readable name, e.g. "Logs". */
    String label();

    /** One-line explanation shown in the picker. */
    String description();

    /** Whether the named material/entity belongs to this group. */
    boolean matches(String key);

    /** Icon used to represent the group in the picker GUI. */
    Material icon();

    /** How many values on this server belong to the group. */
    int memberCount();

    /** The value stored in an objective's target field, e.g. {@code #LOGS}. */
    default String targetValue() {
        return PREFIX + id();
    }
}
