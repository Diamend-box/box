package com.diamend.customachievements.achievement;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A single custom achievement definition.
 *
 * <p>Instances are mutable so the in-game editor can build one up field by
 * field. Use {@link #copy()} when editing an existing achievement so the
 * stored definition is only replaced once the editor is saved.
 */
public class Achievement {

    private String id;
    private String displayName;
    private List<String> description;
    private Material icon;
    private TriggerType trigger;
    private String target;
    private int amount;
    private boolean announce;
    private int rewardXp;
    private List<String> rewardCommands;

    public Achievement(String id) {
        this.id = id;
        this.displayName = "New Achievement";
        this.description = new ArrayList<>(List.of("An amazing feat!"));
        this.icon = Material.NETHER_STAR;
        this.trigger = TriggerType.MANUAL;
        this.target = "ANY";
        this.amount = 1;
        this.announce = true;
        this.rewardXp = 0;
        this.rewardCommands = new ArrayList<>();
    }

    /** Returns a deep, independent copy of this achievement. */
    public Achievement copy() {
        Achievement other = new Achievement(this.id);
        other.displayName = this.displayName;
        other.description = new ArrayList<>(this.description);
        other.icon = this.icon;
        other.trigger = this.trigger;
        other.target = this.target;
        other.amount = this.amount;
        other.announce = this.announce;
        other.rewardXp = this.rewardXp;
        other.rewardCommands = new ArrayList<>(this.rewardCommands);
        return other;
    }

    /** True when this achievement matches the given target key (case-insensitive, ANY = wildcard). */
    public boolean matchesTarget(String key) {
        if (!trigger.usesTarget()) {
            return true;
        }
        if (target == null || target.isBlank() || target.equalsIgnoreCase("ANY")) {
            return true;
        }
        return key != null && target.equalsIgnoreCase(key);
    }

    /** How many units of progress are required to complete this achievement. */
    public int requiredAmount() {
        return trigger.isProgress() ? Math.max(1, amount) : 1;
    }

    // ------------------------------------------------------------------
    // Getters / setters
    // ------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
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

    public boolean isAnnounce() {
        return announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public void setRewardXp(int rewardXp) {
        this.rewardXp = Math.max(0, rewardXp);
    }

    public List<String> getRewardCommands() {
        return rewardCommands;
    }

    public void setRewardCommands(List<String> rewardCommands) {
        this.rewardCommands = rewardCommands;
    }
}
