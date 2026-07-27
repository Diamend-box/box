package com.diamend.boxcore.collection;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A tracked collection — one counter fed by one or more materials, with tiers
 * that pay out as the counter grows.
 */
public class ItemCollection {

    private final String id;
    private String display;
    private String categoryId = "general";
    private Material icon;
    private final Set<Material> materials = new LinkedHashSet<>();
    private final List<CollectionTier> tiers = new ArrayList<>();

    public ItemCollection(String id) {
        this.id = id;
        this.display = id;
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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId == null || categoryId.isBlank() ? "general" : categoryId;
    }

    /** Menu icon; defaults to the first tracked material. */
    public Material getIcon() {
        if (icon != null) {
            return icon;
        }
        return materials.isEmpty() ? Material.CHEST : materials.iterator().next();
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public boolean counts(Material material) {
        return material != null && materials.contains(material);
    }

    public List<CollectionTier> getTiers() {
        return tiers;
    }

    public int tierCount() {
        return tiers.size();
    }

    /** How many tiers a given lifetime amount has earned. */
    public int tierFor(long amount) {
        int tier = 0;
        for (CollectionTier candidate : tiers) {
            if (amount >= candidate.amount()) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    /** The 1-based tier, or null when the collection is already maxed. */
    public CollectionTier nextTier(long amount) {
        int tier = tierFor(amount);
        return tier >= tiers.size() ? null : tiers.get(tier);
    }

    /** Amount still needed for the next tier, or 0 when maxed. */
    public long remainingFor(long amount) {
        CollectionTier next = nextTier(amount);
        return next == null ? 0 : Math.max(0, next.amount() - amount);
    }

    /** Progress toward the next tier, 0.0-1.0 (1.0 when maxed). */
    public double progress(long amount) {
        int tier = tierFor(amount);
        if (tier >= tiers.size()) {
            return 1.0;
        }
        long floor = tier == 0 ? 0 : tiers.get(tier - 1).amount();
        long ceiling = tiers.get(tier).amount();
        if (ceiling <= floor) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) (amount - floor) / (double) (ceiling - floor)));
    }
}
