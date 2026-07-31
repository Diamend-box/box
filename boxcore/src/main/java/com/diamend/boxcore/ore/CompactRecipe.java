package com.diamend.boxcore.ore;

import org.bukkit.Material;

/**
 * One compaction recipe: how many of an item fold into a single compressed unit,
 * and how that unit looks.
 *
 * <p>Recipes are not limited to ore. Anything a server owner adds a recipe for
 * can be compacted, which is the point — a compactor that ignores cobblestone
 * isn't much of a compactor.
 *
 * @param id     the key it is stored under, and what a compactor slot names
 * @param input  the material folded up
 * @param amount how many input items make one unit
 * @param look   how the resulting unit renders
 */
public record CompactRecipe(String id,
                            Material input,
                            int amount,
                            CompressedOre.Appearance look) {

    public CompactRecipe {
        amount = Math.max(2, amount);
    }

    /** The recipe's own look, or the built-in one when it has none. */
    public CompressedOre.Appearance appearance() {
        return look == null ? CompressedOre.Appearance.defaultFor(input) : look;
    }

    /** {@code RAW_IRON} → {@code Iron}. */
    public String display() {
        return CompressedOre.displayName(input);
    }

    /** Whether this recipe's compressed form can be placed as a block. */
    public boolean isPlaceable() {
        return appearance().materialOr(input).isBlock();
    }
}
