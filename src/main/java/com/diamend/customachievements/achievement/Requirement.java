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

    // Lazily parsed REACH_LOCATION target, cached against the source string.
    private transient LocationTarget cachedLocation;
    private transient String cachedLocationSource;

    public Requirement() {
        this(TriggerType.MANUAL, "ANY", 1);
    }

    public Requirement(TriggerType trigger, String target, int amount) {
        this.trigger = trigger;
        this.target = target;
        this.amount = Math.max(1, amount);
    }

    public Requirement copy() {
        return new Requirement(trigger, target, amount);
    }

    /** True when this requirement matches the given target key (ANY = wildcard). */
    public boolean matchesTarget(String key) {
        if (!trigger.usesTarget()) {
            return true;
        }
        if (target == null || target.isBlank() || target.equalsIgnoreCase("ANY")) {
            return true;
        }
        return key != null && target.equalsIgnoreCase(key);
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
            case AURASKILLS_LEVEL -> "Reach level " + amount
                    + (target != null && !target.equalsIgnoreCase("ANY") ? " in " + target : " in any skill");
            default -> {
                String verb = trigger.display();
                if (trigger.usesTarget() && target != null && !target.equalsIgnoreCase("ANY")) {
                    yield verb + ": " + target + " x" + amount;
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
