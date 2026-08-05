package com.diamend.customachievements.achievement;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A single custom achievement definition.
 *
 * <p>An achievement carries presentation/reward data plus a list of
 * {@link Requirement}s (objectives). It is unlocked when <em>every</em>
 * requirement is complete.
 *
 * <p>For backward compatibility with the original single-trigger design, the
 * {@code getTrigger()/getTarget()/getAmount()} helpers delegate to the first
 * requirement, so existing callers (and the current editor) keep working.
 */
public class Achievement {

    private String id;
    private String displayName;
    private List<String> description;
    private Material icon;
    private boolean announce;
    private boolean hidden;
    private String category;
    private int rewardXp;
    private List<String> rewardCommands;
    private final List<ItemStack> rewardItems = new ArrayList<>();
    private final List<Requirement> requirements = new ArrayList<>();

    public Achievement(String id) {
        this.id = id;
        this.displayName = "New Achievement";
        this.description = new ArrayList<>(List.of("An amazing feat!"));
        this.icon = Material.NETHER_STAR;
        this.announce = true;
        this.hidden = false;
        this.category = "";
        this.rewardXp = 0;
        this.rewardCommands = new ArrayList<>();
        this.requirements.add(new Requirement());
    }

    /** Returns a deep, independent copy of this achievement. */
    public Achievement copy() {
        Achievement other = new Achievement(this.id);
        other.displayName = this.displayName;
        other.description = new ArrayList<>(this.description);
        other.icon = this.icon;
        other.announce = this.announce;
        other.hidden = this.hidden;
        other.category = this.category;
        other.rewardXp = this.rewardXp;
        other.rewardCommands = new ArrayList<>(this.rewardCommands);
        for (ItemStack item : this.rewardItems) {
            other.rewardItems.add(item.clone());
        }
        other.requirements.clear();
        for (Requirement requirement : this.requirements) {
            other.requirements.add(requirement.copy());
        }
        return other;
    }

    // ------------------------------------------------------------------
    // Requirements
    // ------------------------------------------------------------------

    public List<Requirement> getRequirements() {
        return requirements;
    }

    /** The first requirement, creating one if the list is somehow empty. */
    public Requirement primary() {
        if (requirements.isEmpty()) {
            requirements.add(new Requirement());
        }
        return requirements.get(0);
    }

    // ------------------------------------------------------------------
    // Presentation / rewards
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

    public boolean isAnnounce() {
        return announce;
    }

    public void setAnnounce(boolean announce) {
        this.announce = announce;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getCategory() {
        return category == null ? "" : category;
    }

    public void setCategory(String category) {
        this.category = category == null ? "" : category.trim();
    }

    public List<ItemStack> getRewardItems() {
        return rewardItems;
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

    // ------------------------------------------------------------------
    // Single-requirement convenience delegates (used by the editor).
    // ------------------------------------------------------------------

    public TriggerType getTrigger() {
        return primary().getTrigger();
    }

    public void setTrigger(TriggerType trigger) {
        primary().setTrigger(trigger);
    }

    public String getTarget() {
        return primary().getTarget();
    }

    public void setTarget(String target) {
        primary().setTarget(target);
    }

    public int getAmount() {
        return primary().getAmount();
    }

    public void setAmount(int amount) {
        primary().setAmount(amount);
    }

    public int requiredAmount() {
        return primary().requiredAmount();
    }

    public boolean matchesTarget(String key) {
        return primary().matchesTarget(key);
    }

    public LocationTarget getLocationTarget() {
        return primary().getLocationTarget();
    }
}
