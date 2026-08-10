package com.diamend.customachievements.achievement;

import java.util.List;
import java.util.Locale;

/**
 * Ties the {@link TargetGroup} families to the triggers they apply to: material
 * groups for the block and item triggers, entity groups for {@code ENTITY_KILL}.
 * Resolution is scoped by trigger, so each trigger only ever sees groups that
 * make sense for it.
 */
public final class TargetGroups {

    private TargetGroups() {
    }

    /** Whether a target string refers to a group (i.e. starts with {@code #}). */
    public static boolean isGroup(String target) {
        return target != null && target.startsWith(TargetGroup.PREFIX);
    }

    /** The groups offered for a trigger, in picker order; empty when it has none. */
    public static List<? extends TargetGroup> forTrigger(TriggerType trigger) {
        if (trigger == null) {
            return List.of();
        }
        return switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE, ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN ->
                    List.of(MaterialGroup.values());
            case ENTITY_KILL -> List.of(EntityGroup.values());
            default -> List.of();
        };
    }

    /**
     * Resolves a {@code #GROUP} target for a trigger, or null when the target
     * isn't a group or names one this trigger doesn't support.
     */
    public static TargetGroup resolve(TriggerType trigger, String target) {
        if (!isGroup(target) || trigger == null) {
            return null;
        }
        return switch (trigger) {
            case BLOCK_BREAK, BLOCK_PLACE, ITEM_CRAFT, ITEM_CONSUME, ITEM_OBTAIN ->
                    MaterialGroup.byId(target);
            case ENTITY_KILL -> EntityGroup.byId(target);
            default -> null;
        };
    }

    /**
     * The ids to try for a written reference, in order. Hand-edited YAML tends
     * to use the singular, so {@code #LOG} finds {@code LOGS} and {@code #BOSS}
     * finds {@code BOSSES}.
     */
    static List<String> candidateIds(String raw) {
        String id = normalizeId(raw);
        return id == null ? List.of() : List.of(id, id + "S", id + "ES");
    }

    /**
     * Normalises a written group reference to a bare id: strips the leading
     * {@code #} and any {@code minecraft:} namespace, upper-cases it and turns
     * spaces into underscores. Returns null for input that can't be an id.
     */
    static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim();
        if (id.startsWith(TargetGroup.PREFIX)) {
            id = id.substring(TargetGroup.PREFIX.length());
        }
        int colon = id.indexOf(':');
        if (colon >= 0) {
            id = id.substring(colon + 1);
        }
        id = id.toUpperCase(Locale.ROOT).replace(' ', '_');
        return id.isBlank() ? null : id;
    }
}
