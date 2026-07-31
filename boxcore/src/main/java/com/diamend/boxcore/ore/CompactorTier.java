package com.diamend.boxcore.ore;

import org.bukkit.Material;

/**
 * One grade of personal compactor: what it is called, how it looks, and how
 * many recipes it can hold at once.
 *
 * @param level    the tier number, used by the give command and stored on the item
 * @param name     display name of the item
 * @param slots    how many recipes it can be configured with
 * @param material what the item renders as
 * @param glow     whether it carries an enchantment glint
 */
public record CompactorTier(int level, String name, int slots, Material material, boolean glow) {

    public CompactorTier {
        slots = Math.max(1, slots);
        material = material == null || material.isAir() ? Material.HOPPER : material;
    }
}
