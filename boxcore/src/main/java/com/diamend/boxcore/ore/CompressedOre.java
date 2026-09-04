package com.diamend.boxcore.ore;

import com.diamend.boxcore.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compressed-ore item: a stack of raw ore folded into a single item.
 *
 * <p>Two things are stored on the item, and both matter. The <em>ratio</em> is
 * what a unit is worth. The <em>source ore</em> is which ore it is worth it in,
 * and it has to be written down rather than read off the item's material,
 * because the material is skinnable — a server owner can make compressed coal
 * look like anything they want, and the counting path still has to know it is
 * coal.
 *
 * <p>Items written by an earlier version carry no source tag. Those fall back
 * to the item's own material, which is exactly what they used to mean, so old
 * stacks keep their value instead of reading as worthless.
 */
public final class CompressedOre {

    /**
     * How a compressed unit looks. Every field is optional in config; the
     * defaults reproduce the built-in appearance, which is the raw ore itself.
     */
    public record Appearance(Material material,
                             String name,
                             List<String> lore,
                             int modelData,
                             boolean glow) {

        /**
         * The built-in look for an ore: the ore's own item, named, described
         * and glinting.
         *
         * <p>The glint is not decoration. A compacted unit is the same material
         * as the raw item by default, so without it the two sit side by side in
         * a hotbar looking identical, and a player right-clicking the wrong one
         * gets no reaction and concludes the feature is broken. A server that
         * skins its units distinctly can turn it off per recipe.
         */
        public static Appearance defaultFor(Material ore) {
            return new Appearance(ore, null, null, 0, true);
        }

        /** The material actually used for the item, falling back to the ore. */
        public Material materialOr(Material ore) {
            return material == null || material.isAir() ? ore : material;
        }
    }

    private final NamespacedKey ratioKey;
    private final NamespacedKey oreKey;

    public CompressedOre(Plugin plugin) {
        this.ratioKey = new NamespacedKey(plugin, "compressed");
        this.oreKey = new NamespacedKey(plugin, "compressed_ore");
    }

    /** Builds a stack of compressed units with the built-in appearance. */
    public ItemStack create(Material ore, int ratio, int units) {
        return create(ore, Appearance.defaultFor(ore), ratio, units);
    }

    /**
     * Builds a stack of compressed units.
     *
     * @param ore        the raw ore this is worth, regardless of how it looks
     * @param appearance how it should render
     * @param ratio      how many raw items one unit represents
     * @param units      how many units the returned stack holds
     */
    public ItemStack create(Material ore, Appearance appearance, int ratio, int units) {
        Appearance look = appearance == null ? Appearance.defaultFor(ore) : appearance;
        ItemStack item = new ItemStack(look.materialOr(ore), Math.max(1, units));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // No metadata means no tag, and an untagged stack would silently be
            // worth 1 apiece — hand back nothing rather than lose the ore.
            return null;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(ratioKey, PersistentDataType.INTEGER, ratio);
        data.set(oreKey, PersistentDataType.STRING, ore.name());

        meta.displayName(Text.item(resolve(
                look.name() == null ? "<aqua>Compressed " + displayName(ore) : look.name(),
                ore, ratio)));
        meta.lore(lore(look, ore, ratio));
        if (look.modelData() > 0) {
            meta.setCustomModelData(look.modelData());
        }
        if (look.glow()) {
            meta.setEnchantmentGlintOverride(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<net.kyori.adventure.text.Component> lore(Appearance look, Material ore, int ratio) {
        List<String> lines = look.lore();
        if (lines == null || lines.isEmpty()) {
            lines = List.of(
                    "<gray>Worth <white><ratio></white> <ore>.",
                    "<dark_gray>Right-click to expand one.",
                    "<dark_gray>Drops on death like raw ore.");
        }
        List<net.kyori.adventure.text.Component> parsed = new ArrayList<>();
        for (String line : lines) {
            parsed.add(Text.item(resolve(line, ore, ratio)));
        }
        return parsed;
    }

    private String resolve(String text, Material ore, int ratio) {
        return text.replace("<ratio>", Text.number(ratio))
                .replace("<ore>", displayName(ore).toLowerCase(Locale.ROOT))
                .replace("<Ore>", displayName(ore));
    }

    /** How many raw items one item of this stack is worth, or 0 when untagged. */
    public int ratio(ItemStack item) {
        PersistentDataContainer data = data(item);
        if (data == null) {
            return 0;
        }
        Integer stored = data.get(ratioKey, PersistentDataType.INTEGER);
        return stored == null || stored <= 0 ? 0 : stored;
    }

    /**
     * Which raw ore a compressed unit is worth, or null when the item is not
     * compressed. Falls back to the item's own material for stacks written
     * before the source ore was recorded separately.
     */
    public Material sourceOre(ItemStack item) {
        PersistentDataContainer data = data(item);
        if (data == null || ratio(item) <= 0) {
            return null;
        }
        String stored = data.get(oreKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return item.getType();
        }
        Material material = Material.matchMaterial(stored.trim().toUpperCase(Locale.ROOT));
        return material == null || material.isAir() ? item.getType() : material;
    }

    public boolean isCompressed(ItemStack item) {
        return ratio(item) > 0;
    }

    /**
     * Whether two stacks are the same kind of thing — both raw, or compressed
     * at the same ratio for the same ore. Used wherever stacks might be merged.
     */
    public boolean sameKind(ItemStack a, ItemStack b) {
        int ratioA = ratio(a);
        int ratioB = ratio(b);
        if (ratioA != ratioB) {
            return false;
        }
        return ratioA <= 0 || sourceOre(a) == sourceOre(b);
    }

    private PersistentDataContainer data(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }

    /** {@code RAW_IRON} → {@code Iron}, {@code LAPIS_LAZULI} → {@code Lapis Lazuli}. */
    public static String displayName(Material material) {
        String name = material.name();
        if (name.startsWith("RAW_")) {
            name = name.substring(4);
        }
        return Text.prettify(name.toLowerCase(Locale.ROOT));
    }
}
