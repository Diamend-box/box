package com.diamend.boxcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Convenience builder for the display items used throughout the GUIs.
 */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material == null ? Material.BARRIER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(name);
            }
            if (lore != null) {
                meta.lore(lore);
            }
            if (glow) {
                // Enchantment-registry independent glow (Paper 1.20.5+).
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack of(Material material, Component name, List<Component> lore) {
        return of(material, name, lore, false);
    }

    public static ItemStack of(Material material, Component name) {
        return of(material, name, null, false);
    }

    /** Builds an item from raw (MiniMessage/legacy) strings. */
    public static ItemStack text(Material material, String name, List<String> lore, boolean glow) {
        List<Component> parsed = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                parsed.add(Text.item(line));
            }
        }
        return of(material, Text.item(name), parsed, glow);
    }

    /** A blank decorative pane used to fill empty GUI slots. */
    public static ItemStack filler(Material material) {
        return of(material, Component.text(" "));
    }

    /** A material's name the way it should read in a menu: {@code Ender Pearl}. */
    public static String prettyName(Material material) {
        return material == null
                ? "" : Text.prettify(material.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** Resolves a config material name, falling back when it isn't recognised. */
    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null || material.isAir()) {
            return fallback;
        }
        return material;
    }

    /**
     * Stack size clamped to what an inventory slot can show — used to display a
     * "you have N" count on an icon without producing an illegal stack.
     */
    public static ItemStack withCount(ItemStack item, long count) {
        item.setAmount((int) Math.max(1, Math.min(64, count)));
        return item;
    }
}
