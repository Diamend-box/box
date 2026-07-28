package com.diamend.boxcore.ore;

import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * The compressed-ore item: a stack of raw ore folded into a single item.
 *
 * <p>The base material is the raw ore itself, which is what makes the item
 * non-placeable without any building restriction — {@code RAW_IRON} is an item,
 * {@code RAW_IRON_BLOCK} is the block, and only the former is ever used here.
 *
 * <p>The unit size lives in the item's persistent data rather than in its name,
 * so renaming one in an anvil cannot change what it is worth. It also keeps
 * compressed and raw stacks from merging: vanilla only stacks items with equal
 * metadata, so a tagged stack and an untagged one of the same material stay
 * separate on their own, with nothing to enforce.
 */
public final class CompressedOre {

    private final NamespacedKey key;

    public CompressedOre(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "compressed");
    }

    /**
     * Builds a stack of compressed units.
     *
     * @param base  the raw ore material being compressed
     * @param ratio how many raw items one unit represents
     * @param units how many units the returned stack holds
     */
    public ItemStack create(Material base, int ratio, int units) {
        ItemStack item = new ItemStack(base, Math.max(1, units));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // No metadata means no tag, and an untagged stack would silently be
            // worth 1 apiece — hand back nothing rather than lose the player's ore.
            return null;
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, ratio);
        meta.displayName(Text.item("<aqua>Compressed " + displayName(base)));
        meta.lore(List.of(
                Text.item("<gray>Worth <white>" + Text.number(ratio) + "</white> " + displayName(base).toLowerCase(Locale.ROOT) + "."),
                Text.item("<dark_gray>Right-click to expand one."),
                Text.item("<dark_gray>Drops on death like raw ore.")));
        item.setItemMeta(meta);
        return item;
    }

    /** How many raw items one item of this stack is worth, or 0 when untagged. */
    public int ratio(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Integer stored = data.get(key, PersistentDataType.INTEGER);
        return stored == null || stored <= 0 ? 0 : stored;
    }

    public boolean isCompressed(ItemStack item) {
        return ratio(item) > 0;
    }

    /** {@code RAW_IRON} → {@code Iron}, {@code LAPIS_LAZULI} → {@code Lapis Lazuli}. */
    public static String displayName(Material material) {
        String name = material.name();
        if (name.startsWith("RAW_")) {
            name = name.substring(4);
        }
        return Text.prettify(name.toLowerCase(Locale.ROOT));
    }

    public NamespacedKey key() {
        return key;
    }
}
