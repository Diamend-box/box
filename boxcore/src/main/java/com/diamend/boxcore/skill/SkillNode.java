package com.diamend.boxcore.skill;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * A single unlockable node in a {@link SkillTree}.
 */
public class SkillNode {

    /** A prerequisite: a node in the same tree, at a minimum level. */
    public record Requirement(String nodeId, int level) {
    }

    private final String treeId;
    private final String id;

    private String display;
    private List<String> description = new ArrayList<>();
    private Material icon = Material.BOOK;
    private Material lockedIcon;
    private int slot;
    private int maxLevel = 1;
    /** Cost of each level; index 0 is the first level. */
    private List<Integer> costs = new ArrayList<>(List.of(1));
    private List<Requirement> requirements = new ArrayList<>();
    private int requiredPointsInTree;
    private String permission = "";
    private final NodeEffects effects = new NodeEffects();

    public SkillNode(String treeId, String id) {
        this.treeId = treeId;
        this.id = id;
        this.display = id;
    }

    /** Globally unique key, {@code tree.node} — what player data is keyed by. */
    public String key() {
        return treeId + "." + id;
    }

    public String getTreeId() {
        return treeId;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
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

    public Material getLockedIcon() {
        return lockedIcon;
    }

    public void setLockedIcon(Material lockedIcon) {
        this.lockedIcon = lockedIcon;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = Math.max(1, maxLevel);
    }

    public List<Integer> getCosts() {
        return costs;
    }

    public void setCosts(List<Integer> costs) {
        this.costs = costs == null || costs.isEmpty() ? new ArrayList<>(List.of(1)) : costs;
    }

    /**
     * Point cost of moving from {@code currentLevel} to the next one. Levels
     * beyond the configured cost list reuse the last cost, so
     * {@code cost: 2, max-level: 5} means "2 points every time".
     */
    public int costForLevel(int currentLevel) {
        if (costs.isEmpty()) {
            return 1;
        }
        int index = Math.min(Math.max(0, currentLevel), costs.size() - 1);
        return Math.max(0, costs.get(index));
    }

    /** Total points sunk into this node at the given level. */
    public int totalCostAt(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += costForLevel(i);
        }
        return total;
    }

    public List<Requirement> getRequirements() {
        return requirements;
    }

    public void setRequirements(List<Requirement> requirements) {
        this.requirements = requirements;
    }

    public int getRequiredPointsInTree() {
        return requiredPointsInTree;
    }

    public void setRequiredPointsInTree(int requiredPointsInTree) {
        this.requiredPointsInTree = Math.max(0, requiredPointsInTree);
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission;
    }

    public NodeEffects getEffects() {
        return effects;
    }
}
