package com.diamend.darksea.armor;

import com.diamend.darksea.config.DarkSeaSettings.ArmorSettings;
import com.diamend.darksea.config.DarkSeaSettings.ArmorStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Item factory and tag reader for sea armor and boat upgrade tokens. The
 * authority on what an item "is" lives in its PersistentDataContainer —
 * names and lore are cosmetic, tags survive anvils, and nothing can be
 * faked with a renamed vanilla piece.
 */
public final class SeaArmor {

    /** Integer PDC tag: which sea-armor tier a piece belongs to. */
    public static final NamespacedKey TIER_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:tier"));
    /** Integer PDC tag: which boat level a token unlocks. */
    public static final NamespacedKey TOKEN_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("darksea:boat_token"));

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public enum Piece {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS;

        String title() {
            String lower = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

    private SeaArmor() {
    }

    /** One armor piece of the given tier, or null if the tier has no style. */
    public static ItemStack createPiece(ArmorSettings armor, int tier, Piece piece) {
        ArmorStyle style = armor.tiers().get(tier);
        if (style == null) {
            return null;
        }
        Material material = Material.matchMaterial(style.materialPrefix() + "_" + piece.name());
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(style.displayName() + " " + piece.title())));
        meta.lore(List.of(noItalic(MM.deserialize("<gray>Dark Sea protection tier <aqua>" + tier + "</aqua>"))));
        if (armor.unbreakable()) {
            meta.setUnbreakable(true);
        }
        meta.getPersistentDataContainer().set(TIER_KEY, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);
        return item;
    }

    /** The full four-piece set for a tier (empty list if the tier is undefined). */
    public static List<ItemStack> createSet(ArmorSettings armor, int tier) {
        List<ItemStack> set = new ArrayList<>(4);
        for (Piece piece : Piece.values()) {
            ItemStack item = createPiece(armor, tier, piece);
            if (item != null) {
                set.add(item);
            }
        }
        return set;
    }

    /** The sea-armor tier of an item; 0 for null, untagged or vanilla items. */
    public static int tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer tier = item.getItemMeta().getPersistentDataContainer()
                .get(TIER_KEY, PersistentDataType.INTEGER);
        return tier != null ? tier : 0;
    }

    /**
     * Effective protection = the LOWEST tier across the four armor slots.
     * A missing or untagged piece is tier 0, so full commitment to a set is
     * required — one Leviathan boot over leather protects nothing.
     */
    public static int effectiveTier(ItemStack[] armorContents) {
        int min = Integer.MAX_VALUE;
        int slots = 0;
        for (ItemStack item : armorContents) {
            min = Math.min(min, tierOf(item));
            slots++;
        }
        return slots == 0 ? 0 : Math.max(0, min == Integer.MAX_VALUE ? 0 : min);
    }

    public static ItemStack createToken(int level) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(MM.deserialize(
                "<gold>Boat Upgrade Token</gold> <gray>(level " + level + ")</gray>")));
        meta.lore(List.of(
                noItalic(MM.deserialize("<gray>Unlocks boat level <aqua>" + level + "</aqua>.")),
                noItalic(MM.deserialize("<gray>Hold it and run <white>/ds boat upgrade</white>."))));
        meta.getPersistentDataContainer().set(TOKEN_KEY, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        return item;
    }

    /** The boat level a token unlocks; 0 if the item is not a token. */
    public static int tokenLevelOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(TOKEN_KEY, PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
