package com.diamend.customachievements.achievement;

import java.util.Objects;

/**
 * A single objective within an {@link Achievement}. An achievement is complete
 * when <em>all</em> of its requirements are complete.
 *
 * <p>Each requirement is one trigger + optional target + required amount, and
 * tracks its own progress (keyed per-requirement in the player's data).
 */
public class Requirement {

    private TriggerType trigger;
    private String target;
    private int amount;
    private boolean matchByName;

    // Lazily parsed REACH_LOCATION target, cached against the source string.
    private transient LocationTarget cachedLocation;
    private transient String cachedLocationSource;

    // Lazily resolved "#GROUP" target, cached against the source string.
    private transient TargetGroup cachedGroup;
    private transient String cachedGroupSource;
    private transient TriggerType cachedGroupTrigger;

    public Requirement() {
        this(TriggerType.MANUAL, "ANY", 1);
    }

    public Requirement(TriggerType trigger, String target, int amount) {
        this.trigger = trigger;
        this.target = target;
        this.amount = Math.max(1, amount);
    }

    public Requirement copy() {
        Requirement other = new Requirement(trigger, target, amount);
        other.matchByName = this.matchByName;
        return other;
    }

    /** True when this requirement matches the given target key (ANY = wildcard). */
    public boolean matchesTarget(String key) {
        if (!trigger.usesTarget()) {
            return true;
        }
        if (isWildcard()) {
            return true;
        }
        TargetGroup group = getGroup();
        if (group != null) {
            return group.matches(key);
        }
        return key != null && target.equalsIgnoreCase(key);
    }

    /**
     * Item matching: when {@link #matchByName} is set, compares the target
     * against the item's custom display name; otherwise against its material
     * (which may be a {@code #GROUP} covering a whole family of materials).
     */
    public boolean matchesItem(String materialName, String itemName) {
        if (isWildcard()) {
            return true;
        }
        if (matchByName) {
            return itemName != null && target.equalsIgnoreCase(itemName);
        }
        TargetGroup group = getGroup();
        if (group != null) {
            return group.matches(materialName);
        }
        return materialName != null && target.equalsIgnoreCase(materialName);
    }

    /** True when the target matches everything (unset or "ANY"). */
    private boolean isWildcard() {
        return target == null || target.isBlank() || target.equalsIgnoreCase("ANY");
    }

    /**
     * The group this requirement targets (a material family for block/item
     * triggers, a mob family for {@code ENTITY_KILL}), or null when it targets a
     * single value. A {@code #GROUP} that this trigger doesn't support — or one
     * written by a newer version — resolves to null and so matches nothing.
     */
    public TargetGroup getGroup() {
        if (!TargetGroups.isGroup(target)) {
            return null;
        }
        if (!Objects.equals(cachedGroupSource, target) || cachedGroupTrigger != trigger) {
            cachedGroup = TargetGroups.resolve(trigger, target);
            cachedGroupSource = target;
            cachedGroupTrigger = trigger;
        }
        return cachedGroup;
    }

    public boolean isMatchByName() {
        return matchByName;
    }

    public void setMatchByName(boolean matchByName) {
        this.matchByName = matchByName;
    }

    /**
     * Identifies what this requirement asks for, so the statistics backfill can
     * seed it exactly once per player — and seed it afresh if the objective is
     * later edited to ask for something different.
     */
    public String backfillSignature() {
        return trigger.name() + ":" + (target == null ? "" : target) + (matchByName ? ":name" : "");
    }

    /** Units of progress needed to finish this requirement. */
    public int requiredAmount() {
        return trigger.isProgress() ? Math.max(1, amount) : 1;
    }

    /** Parsed location target for REACH_LOCATION requirements, or null. */
    public LocationTarget getLocationTarget() {
        if (!Objects.equals(cachedLocationSource, target)) {
            cachedLocation = LocationTarget.parse(target);
            cachedLocationSource = target;
        }
        return cachedLocation;
    }

    /**
     * The target in human-readable form: {@code Any Logs} for a group, a quoted
     * custom item name when matching by name, {@code Any} for the wildcard, and
     * otherwise the raw value (e.g. {@code OAK_LOG}).
     */
    public String targetLabel() {
        if (isWildcard()) {
            return "Any";
        }
        if (matchByName) {
            return "\"" + target + "\"";
        }
        TargetGroup group = getGroup();
        return group != null ? "Any " + group.label() : target;
    }

    /** A short, plain-text description used in the achievements menu. */
    public String describe() {
        return switch (trigger) {
            case MANUAL -> "Granted by staff";
            case REACH_LOCATION -> {
                LocationTarget loc = getLocationTarget();
                yield "Reach " + (loc != null ? loc.pretty() : target);
            }
            case REACH_DIMENSION -> "Enter " + target + (amount > 1 ? " (x" + amount + ")" : "");
            case PLAYTIME_HOURS -> "Play for " + amount + " hour(s)";
            case PLAYER_DEATH -> isWildcard()
                    ? "Die " + amount + " time(s)"
                    : "Die to " + targetLabel() + " x" + amount;
            case ITEM_HAVE -> "Have " + targetLabel() + " x" + amount + " at once";
            case AURASKILLS_LEVEL -> "Reach level " + amount
                    + (target != null && !target.equalsIgnoreCase("ANY") ? " in " + target : " in any skill");
            default -> {
                String verb = trigger.display();
                if (trigger.usesTarget() && !isWildcard()) {
                    yield verb + ": " + targetLabel() + " x" + amount;
                }
                yield verb + " x" + amount;
            }
        };
    }

    public TriggerType getTrigger() {
        return trigger;
    }

    public void setTrigger(TriggerType trigger) {
        this.trigger = trigger;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }
}
